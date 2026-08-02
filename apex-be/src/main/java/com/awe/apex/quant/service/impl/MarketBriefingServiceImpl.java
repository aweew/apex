package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.dto.MarketFactorItem;
import com.awe.apex.quant.domain.dto.MarketIndexItem;
import com.awe.apex.quant.domain.dto.MarketTipItem;
import com.awe.apex.quant.domain.dto.SectorBoardItem;
import com.awe.apex.quant.domain.entity.IndexBar;
import com.awe.apex.quant.domain.entity.LimitUpPool;
import com.awe.apex.quant.domain.entity.SectorQuote;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.mapper.IndexBarMapper;
import com.awe.apex.quant.mapper.LimitUpPoolMapper;
import com.awe.apex.quant.mapper.SectorQuoteMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.market.TradingCalendar;
import com.awe.apex.quant.service.IMarketBriefingService;
import com.awe.apex.quant.service.ISectorBoardService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 每日市场简报：大盘趋势 + 风格 + 量能 + 涨停情绪 + 主线题材
 */
@Slf4j
@Service
public class MarketBriefingServiceImpl implements IMarketBriefingService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final long CACHE_TTL_MS = 180_000L;

    private final Object cacheLock = new Object();
    private MarketBriefingResp cachedBriefing;
    private long cachedAtMs;

    @Resource
    private IndexBarMapper indexBarMapper;

    @Resource
    private LimitUpPoolMapper limitUpPoolMapper;

    @Resource
    private SectorQuoteMapper sectorQuoteMapper;

    @Resource
    private ISectorBoardService sectorBoardService;

    @Resource
    private StockBasicMapper stockBasicMapper;

    /**
     * 生成市场简报
     *
     * @return 简报
     */
    @Override
    public MarketBriefingResp briefing() {
        long now = System.currentTimeMillis();
        synchronized (cacheLock) {
            if (Objects.nonNull(cachedBriefing) && now - cachedAtMs < CACHE_TTL_MS) {
                return cachedBriefing;
            }
        }
        MarketBriefingResp built = buildBriefing();
        synchronized (cacheLock) {
            cachedBriefing = built;
            cachedAtMs = now;
        }
        return built;
    }

    private MarketBriefingResp buildBriefing() {
        List<IndexBar> sh = loadBars("CN_SH", 60);
        List<IndexBar> sz = loadBars("CN_SZ", 60);
        List<IndexBar> cyb = loadBars("CN_CYB", 30);
        List<IndexBar> kc = loadBars("CN_KC50", 30);
        List<IndexBar> bj = loadBars("CN_BJ50", 60);

        LocalDate asOf = latestDate(sh, sz, cyb);
        List<MarketFactorItem> factors = new ArrayList<>();
        List<MarketTipItem> tips = new ArrayList<>();
        List<String> indexLines = new ArrayList<>();
        List<MarketIndexItem> indexes = new ArrayList<>();
        int score = 50;

        // —— 大盘当日 ——
        BigDecimal shPct = lastPct(sh);
        BigDecimal szPct = lastPct(sz);
        BigDecimal cybPct = lastPct(cyb);
        BigDecimal kcPct = lastPct(kc);
        indexLines.add(lineOf("上证指数", sh));
        indexLines.add(lineOf("深圳成指", sz));
        indexLines.add(lineOf("创业板指", cyb));
        indexes.add(indexItemOf("上证指数", sh));
        indexes.add(indexItemOf("深圳成指", sz));
        indexes.add(indexItemOf("创业板指", cyb));
        if (CollUtil.isNotEmpty(kc)) {
            indexLines.add(lineOf("科创50", kc));
            indexes.add(indexItemOf("科创50", kc));
        }

        BigDecimal dayAvg = avgNonNull(shPct, cybPct);
        if (Objects.nonNull(dayAvg)) {
            String daySignal = "中性";
            if (dayAvg.compareTo(new BigDecimal("0.8")) >= 0) {
                daySignal = "偏多";
                score += 12;
            } else if (dayAvg.compareTo(new BigDecimal("-0.8")) <= 0) {
                daySignal = "偏空";
                score -= 14;
            } else if (dayAvg.compareTo(ZERO) > 0) {
                score += 4;
            } else if (dayAvg.compareTo(ZERO) < 0) {
                score -= 5;
            }
            factors.add(MarketFactorItem.builder()
                    .name("大盘当日")
                    .value(fmtPct(dayAvg) + "（上证" + fmtPct(shPct) + "/创业" + fmtPct(cybPct) + "）")
                    .signal(daySignal)
                    .note("综合上证与创业板涨跌")
                    .build());
            if (dayAvg.compareTo(new BigDecimal("-1.5")) <= 0) {
                tips.add(tip("danger", "主要指数明显调整，控制开仓与追高，优先处理持仓风险。"));
            } else if (dayAvg.compareTo(new BigDecimal("1.5")) >= 0) {
                tips.add(tip("info", "指数强势上涨，可积极跟踪主线，但仍需避免尾盘情绪透支。"));
            }
        } else {
            factors.add(MarketFactorItem.builder()
                    .name("大盘当日")
                    .value("暂无指数数据")
                    .signal("提示")
                    .note("请先在「大盘」页刷新指数")
                    .build());
            tips.add(tip("warn", "本地缺少指数日线，市场立场仅供参考；请先同步指数数据。"));
        }

        // —— 趋势（相对 MA20）——
        Boolean aboveMa20 = aboveMa(sh, 20);
        BigDecimal ret5 = cumReturn(sh, 5);
        BigDecimal ret20 = cumReturn(sh, 20);
        if (Objects.nonNull(aboveMa20)) {
            if (Boolean.TRUE.equals(aboveMa20)) {
                score += 10;
                factors.add(MarketFactorItem.builder()
                        .name("趋势位置")
                        .value("上证站上MA20")
                        .signal("偏多")
                        .note("近5日" + fmtPct(ret5) + " · 近20日" + fmtPct(ret20))
                        .build());
            } else {
                score -= 12;
                factors.add(MarketFactorItem.builder()
                        .name("趋势位置")
                        .value("上证低于MA20")
                        .signal("偏空")
                        .note("近5日" + fmtPct(ret5) + " · 近20日" + fmtPct(ret20))
                        .build());
                tips.add(tip("warn", "指数仍在均线下方，趋势未修复前宜降低进攻仓位。"));
            }
        }

        // —— 三市成交额（沪+深+京）与量能 ——
        VolumeStat vol = threeMarketAmountStat(sh, sz, bj, 5);
        String volumeTrend = null;
        BigDecimal volumeVsMa5Pct = null;
        BigDecimal indexVolume = null;
        String indexVolumeText = null;
        if (Objects.nonNull(vol)) {
            volumeTrend = vol.trend;
            volumeVsMa5Pct = vol.vsMa5Pct;
            // 展示优先实时三市成交额；库内 amount 常为空
            BigDecimal liveAmount = fetchLiveThreeMarketAmount();
            indexVolume = Objects.nonNull(liveAmount) && liveAmount.signum() > 0 ? liveAmount : vol.volume;
            indexVolumeText = formatAmount(indexVolume);
            String volSignal = "中性";
            if ("放量".equals(vol.trend) && Objects.nonNull(shPct) && shPct.compareTo(ZERO) >= 0) {
                volSignal = "偏多";
                score += 8;
                tips.add(tip("info", "三市放量上涨，资金参与度上升，可关注强度板块与持仓加仓机会。"));
            } else if ("放量".equals(vol.trend) && Objects.nonNull(shPct) && shPct.compareTo(ZERO) < 0) {
                volSignal = "偏空";
                score -= 10;
                tips.add(tip("danger", "三市放量下跌，抛压较重，谨慎抄底，优先减仓弱势持仓。"));
            } else if ("缩量".equals(vol.trend) && Objects.nonNull(shPct) && shPct.compareTo(ZERO) > 0) {
                volSignal = "提示";
                score -= 2;
                tips.add(tip("warn", "三市缩量上涨，上攻动能不足，追高性价比偏低。"));
            } else if ("缩量".equals(vol.trend)) {
                volSignal = "提示";
                tips.add(tip("info", "三市成交缩量，观望资金较多，等待放量确认方向。"));
            }
            String factorValue = Objects.nonNull(indexVolumeText)
                    ? (indexVolumeText + " · " + vol.trend + " · 较5日均额" + fmtPct(vol.vsMa5Pct))
                    : (vol.trend + " · 较5日均额" + fmtPct(vol.vsMa5Pct));
            factors.add(MarketFactorItem.builder()
                    .name("三市成交")
                    .value(factorValue)
                    .signal(volSignal)
                    .note(vol.usedAmount ? "上证+深成+北证50 成交额合计" : "本地成交额缺失，量能用成交量合计近似；总额优先东财实时")
                    .build());
        }

        // —— 风格（创业板/科创 vs 上证，近5日）——
        BigDecimal style5 = styleSpread(cyb, sh, 5);
        BigDecimal styleKc = styleSpread(kc, sh, 5);
        if (Objects.nonNull(style5)) {
            String styleName;
            String styleSignal;
            if (style5.compareTo(new BigDecimal("2")) >= 0) {
                styleName = "成长/小盘占优";
                styleSignal = "偏多";
                score += 5;
                tips.add(tip("info", "近5日创业板显著强于主板，风格偏向成长；选股可优先科技/题材弹性。"));
            } else if (style5.compareTo(new BigDecimal("-2")) <= 0) {
                styleName = "权重/防御占优";
                styleSignal = "提示";
                score -= 3;
                tips.add(tip("info", "近5日主板相对更强，风格偏权重/防御；追题材需更严格。"));
            } else {
                styleName = "风格均衡";
                styleSignal = "中性";
            }
            String extra = Objects.nonNull(styleKc) ? " · 科创相对主板" + fmtPct(styleKc) : "";
            factors.add(MarketFactorItem.builder()
                    .name("最近风格")
                    .value(styleName + "（创业-上证5日" + fmtPct(style5) + "）")
                    .signal(styleSignal)
                    .note("相对强弱" + extra)
                    .build());
        }

        // —— 涨停情绪 ——
        LimitUpStat lu = limitUpStat(asOf);
        if (Objects.nonNull(lu)) {
            String luSignal = "中性";
            if (lu.count >= 80 && lu.maxLianban >= 5) {
                luSignal = "提示";
                score += 3;
                tips.add(tip("warn", "涨停活跃且连板高度达" + lu.maxLianban
                        + "板，短线情绪偏热，注意分歧日回撤。"));
            } else if (lu.count <= 30) {
                luSignal = "偏空";
                score -= 6;
                tips.add(tip("warn", "涨停家数偏少（" + lu.count + "），赚钱效应一般，宜精选不强攻。"));
            } else if (lu.maxLianban >= 7) {
                tips.add(tip("warn", "最高连板" + lu.maxLianban + "，主线抱团明显，跟风需确认晋级率。"));
            } else {
                score += 2;
            }
            factors.add(MarketFactorItem.builder()
                    .name("涨停情绪")
                    .value("涨停" + lu.count + "家 · 最高" + lu.maxLianban + "板")
                    .signal(luSignal)
                    .note(lu.asOf != null ? ("池日期 " + lu.asOf) : "涨停池")
                    .build());
        } else {
            factors.add(MarketFactorItem.builder()
                    .name("涨停情绪")
                    .value("暂无涨停池")
                    .signal("提示")
                    .note("可在「涨停复盘」刷新")
                    .build());
        }

        // —— 市场广度（全市场涨/平/跌，平盘单独计、不并入涨）——
        int[] breadth = marketBreadth(asOf);
        Integer breadthUp = breadth[0] > 0 || breadth[1] > 0 ? breadth[0] : null;
        Integer breadthDown = breadth[0] > 0 || breadth[1] > 0 ? breadth[1] : null;
        Integer breadthFlat = Objects.nonNull(breadthUp) ? breadth[2] : null;
        if (Objects.nonNull(breadthUp)) {
            String bSignal = "中性";
            if (breadthUp > breadthDown * 1.5) {
                bSignal = "偏多";
                score += 6;
            } else if (breadthDown > breadthUp * 1.5) {
                bSignal = "偏空";
                score -= 8;
                tips.add(tip("warn", "下跌家数明显多于上涨，市场广度偏弱。"));
            }
            factors.add(MarketFactorItem.builder()
                    .name("市场广度")
                    .value("涨" + breadthUp + " / 平" + breadthFlat + " / 跌" + breadthDown)
                    .signal(bSignal)
                    .note("全市场涨跌家数（平盘单列）")
                    .build());
        }

        // —— 主线题材 ——
        List<String> hotThemes = hotThemes();
        if (CollUtil.isNotEmpty(hotThemes)) {
            factors.add(MarketFactorItem.builder()
                    .name("主线题材")
                    .value(String.join("、", hotThemes.subList(0, Math.min(3, hotThemes.size()))))
                    .signal("提示")
                    .note("板块涨幅/净流入靠前")
                    .build());
            tips.add(tip("info", "今日关注主线：" + String.join("、", hotThemes.subList(0, Math.min(4, hotThemes.size())))
                    + "；买卖优先与主线同向。"));
        }

        // —— 数据新鲜度门禁 ——
        String dataLevel = resolveDataLevel(sh, asOf, lu);
        boolean dataSufficient = !"RED".equals(dataLevel);
        if ("RED".equals(dataLevel)) {
            score = Math.min(score, 35);
            tips.add(0, tip("danger", "关键行情数据不足或过期，禁止进攻立场；请先同步指数/板块/涨停池。"));
        } else if ("YELLOW".equals(dataLevel)) {
            tips.add(tip("warn", "部分数据偏旧，简报仅供参考，建议先刷新大盘与涨停池。"));
        }

        score = Math.max(0, Math.min(100, score));
        String stance;
        BigDecimal buyFactor;
        String positionAdvice;
        if (!dataSufficient) {
            stance = "防守";
            buyFactor = new BigDecimal("0.40");
            positionAdvice = "数据不足：建议空仓观望或极低仓，先补齐同步";
            tips.add(0, tip("danger", "数据门禁生效：强制防守，买入仓位已大幅降权。"));
        } else if (score >= 65) {
            stance = "进攻";
            buyFactor = new BigDecimal("1.10");
            positionAdvice = "建议总仓 6–8 成，可对共振/主线标的正常/略抬仓";
            tips.add(0, tip("info", "综合评分偏进攻：可执行买入计划，但仍控制单票上限。"));
        } else if (score <= 40) {
            stance = "防守";
            buyFactor = new BigDecimal("0.55");
            positionAdvice = "建议总仓 2–4 成，新建仓降权，优先处理卖出/止损";
            tips.add(0, tip("danger", "综合评分偏防守：今日买入建议已自动降权，优先风控与持仓体检。"));
        } else {
            stance = "均衡";
            buyFactor = BigDecimal.ONE;
            positionAdvice = "建议总仓 4–6 成，精选共振/主线，避免无脑铺开";
            tips.add(0, tip("info", "市场中性偏均衡：有信号再做，仓位中等、纪律优先。"));
        }

        String stanceReason = "评分 " + score + "/100 · 数据" + dataLevel
                + " · 综合大盘、趋势、量能、风格、广度与涨停情绪";
        if (tips.size() > 10) {
            tips = new ArrayList<>(tips.subList(0, 10));
        }

        return MarketBriefingResp.builder()
                .asOf(asOf)
                .stance(stance)
                .stanceScore(score)
                .stanceReason(stanceReason)
                .positionAdvice(positionAdvice)
                .buyWeightFactor(buyFactor)
                .factors(factors)
                .tips(tips)
                .indexLines(indexLines)
                .indexes(indexes)
                .volumeTrend(volumeTrend)
                .volumeVsMa5Pct(volumeVsMa5Pct)
                .indexVolume(indexVolume)
                .indexVolumeText(indexVolumeText)
                .limitDownCount(countLimitDown())
                .hotThemes(hotThemes)
                .dataLevel(dataLevel)
                .dataSufficient(dataSufficient)
                .breadthUp(breadthUp)
                .breadthDown(breadthDown)
                .breadthFlat(breadthFlat)
                .message(Objects.nonNull(asOf)
                        ? ("市场简报 · " + asOf + " · 立场「" + stance + "」· 数据" + dataLevel)
                        : "市场简报（指数数据不足）· 数据" + dataLevel)
                .build();
    }

    /** @return [上涨, 下跌, 平盘]，平盘绝不并入上涨 */
    private int[] marketBreadth(LocalDate asOf) {
        int[] live = fetchLiveMarketBreadth();
        if (live[0] > 0 || live[1] > 0) {
            return live;
        }
        int[] fromBasic = breadthFromStockBasic();
        if (fromBasic[0] > 0 || fromBasic[1] > 0) {
            return fromBasic;
        }
        return breadthFromSectorQuote(asOf);
    }

    /**
     * 东财涨跌分布（全市场），失败时用沪深成份涨跌家数相加兜底
     */
    private int[] fetchLiveMarketBreadth() {
        int[] fromFenbu = fetchBreadthFromZdFenBu();
        if (fromFenbu[0] > 0 || fromFenbu[1] > 0) {
            return fromFenbu;
        }
        return fetchBreadthFromIndexUlist();
    }

    private int[] fetchBreadthFromZdFenBu() {
        String url = "https://push2ex.eastmoney.com/getTopicZDFenBu"
                + "?ut=7eea3edcaed734bea9cbfc24409ed989&dpt=wz.ztzt";
        try (HttpResponse response = HttpRequest.get(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://quote.eastmoney.com/")
                .header("Accept", "application/json,text/plain,*/*")
                .timeout(5000)
                .execute()) {
            if (!response.isOk()) {
                return new int[]{0, 0, 0};
            }
            JSONObject data = JSONUtil.parseObj(response.body()).getJSONObject("data");
            if (Objects.isNull(data)) {
                return new int[]{0, 0, 0};
            }
            JSONArray fenbu = data.getJSONArray("fenbu");
            if (Objects.isNull(fenbu) || fenbu.isEmpty()) {
                return new int[]{0, 0, 0};
            }
            // 档位：>0 上涨，<0 下跌，0 平盘（绝不并入涨）
            int up = 0;
            int down = 0;
            int flat = 0;
            for (int i = 0; i < fenbu.size(); i++) {
                Object raw = fenbu.get(i);
                if (Objects.isNull(raw)) {
                    continue;
                }
                JSONObject bucket = raw instanceof JSONObject
                        ? (JSONObject) raw
                        : JSONUtil.parseObj(raw);
                for (Map.Entry<String, Object> entry : bucket.entrySet()) {
                    int level;
                    try {
                        level = Integer.parseInt(String.valueOf(entry.getKey()).trim());
                    } catch (Exception ignored) {
                        continue;
                    }
                    int count;
                    try {
                        count = new BigDecimal(String.valueOf(entry.getValue())).intValue();
                    } catch (Exception ignored) {
                        continue;
                    }
                    if (level > 0) {
                        up += count;
                    } else if (level < 0) {
                        down += count;
                    } else {
                        flat += count;
                    }
                }
            }
            return new int[]{up, down, flat};
        } catch (Exception ex) {
            log.debug("东财涨跌分布失败: {}", ex.getMessage());
            return new int[]{0, 0, 0};
        }
    }

    private int[] fetchBreadthFromIndexUlist() {
        // f104 上涨 / f105 下跌 / f106 平盘（分列，不合并）
        String url = "https://push2delay.eastmoney.com/api/qt/ulist.np/get"
                + "?fltt=2&secids=1.000001,0.399001&fields=f104,f105,f106,f12";
        try (HttpResponse response = HttpRequest.get(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://quote.eastmoney.com/")
                .header("Accept", "application/json,text/plain,*/*")
                .timeout(5000)
                .execute()) {
            if (!response.isOk()) {
                return new int[]{0, 0, 0};
            }
            JSONObject data = JSONUtil.parseObj(response.body()).getJSONObject("data");
            if (Objects.isNull(data)) {
                return new int[]{0, 0, 0};
            }
            JSONArray diff = data.getJSONArray("diff");
            if (Objects.isNull(diff) || diff.isEmpty()) {
                return new int[]{0, 0, 0};
            }
            int up = 0;
            int down = 0;
            int flat = 0;
            for (int i = 0; i < diff.size(); i++) {
                JSONObject row = diff.getJSONObject(i);
                if (Objects.isNull(row)) {
                    continue;
                }
                up += row.getInt("f104", 0);
                down += row.getInt("f105", 0);
                flat += row.getInt("f106", 0);
            }
            return new int[]{up, down, flat};
        } catch (Exception ex) {
            log.debug("东财指数涨跌家数失败: {}", ex.getMessage());
            return new int[]{0, 0, 0};
        }
    }

    private int[] breadthFromStockBasic() {
        try {
            Long up = stockBasicMapper.selectCount(Wrappers.<StockBasic>lambdaQuery()
                    .isNotNull(StockBasic::getPctChg)
                    .gt(StockBasic::getPctChg, ZERO));
            Long down = stockBasicMapper.selectCount(Wrappers.<StockBasic>lambdaQuery()
                    .isNotNull(StockBasic::getPctChg)
                    .lt(StockBasic::getPctChg, ZERO));
            Long flat = stockBasicMapper.selectCount(Wrappers.<StockBasic>lambdaQuery()
                    .isNotNull(StockBasic::getPctChg)
                    .eq(StockBasic::getPctChg, ZERO));
            return new int[]{
                    Objects.nonNull(up) ? up.intValue() : 0,
                    Objects.nonNull(down) ? down.intValue() : 0,
                    Objects.nonNull(flat) ? flat.intValue() : 0
            };
        } catch (Exception ex) {
            return new int[]{0, 0, 0};
        }
    }

    private int[] breadthFromSectorQuote(LocalDate asOf) {
        int up = 0;
        int down = 0;
        LocalDate day = asOf;
        List<SectorQuote> rows = new ArrayList<>();
        if (Objects.nonNull(day)) {
            rows = sectorQuoteMapper.selectList(Wrappers.<SectorQuote>lambdaQuery()
                    .eq(SectorQuote::getBoardType, "INDUSTRY")
                    .eq(SectorQuote::getTradeDate, day));
        }
        if (CollUtil.isEmpty(rows)) {
            SectorQuote latest = sectorQuoteMapper.selectOne(Wrappers.<SectorQuote>lambdaQuery()
                    .eq(SectorQuote::getBoardType, "INDUSTRY")
                    .orderByDesc(SectorQuote::getTradeDate)
                    .last("LIMIT 1"));
            if (Objects.isNull(latest)) {
                return new int[]{0, 0, 0};
            }
            day = latest.getTradeDate();
            rows = sectorQuoteMapper.selectList(Wrappers.<SectorQuote>lambdaQuery()
                    .eq(SectorQuote::getBoardType, "INDUSTRY")
                    .eq(SectorQuote::getTradeDate, day));
        }
        for (SectorQuote row : rows) {
            if (Objects.nonNull(row.getUpCount())) {
                up += row.getUpCount();
            }
            if (Objects.nonNull(row.getDownCount())) {
                down += row.getDownCount();
            }
        }
        // 板块汇总无平盘字段，且存在重复计票，仅作兜底
        return new int[]{up, down, 0};
    }

    private String resolveDataLevel(List<IndexBar> sh, LocalDate asOf, LimitUpStat lu) {
        LocalDate latestTrade = TradingCalendar.latestTradingDayOnOrBefore(LocalDate.now());
        // 允许落后最近 3 个交易日
        List<LocalDate> recent3 = TradingCalendar.recentTradingDays(latestTrade, 3);
        LocalDate oldestOk = recent3.get(0);

        boolean hasIndex = CollUtil.isNotEmpty(sh) && Objects.nonNull(asOf);
        boolean indexFresh = false;
        if (hasIndex) {
            LocalDate latest = sh.get(sh.size() - 1).getTradeDate();
            indexFresh = Objects.nonNull(latest) && !latest.isBefore(oldestOk);
        }
        boolean hasLu = Objects.nonNull(lu)
                && Objects.nonNull(lu.asOf)
                && !lu.asOf.isBefore(oldestOk);
        SectorQuote sectorLatest = sectorQuoteMapper.selectOne(Wrappers.<SectorQuote>lambdaQuery()
                .eq(SectorQuote::getBoardType, "INDUSTRY")
                .orderByDesc(SectorQuote::getTradeDate)
                .last("LIMIT 1"));
        boolean hasSector = Objects.nonNull(sectorLatest)
                && Objects.nonNull(sectorLatest.getTradeDate())
                && !sectorLatest.getTradeDate().isBefore(oldestOk);
        if (!hasIndex || !indexFresh) {
            return "RED";
        }
        if (!hasSector || !hasLu) {
            return "YELLOW";
        }
        return "GREEN";
    }

    private List<IndexBar> loadBars(String code, int limit) {
        List<IndexBar> desc = indexBarMapper.selectList(Wrappers.<IndexBar>lambdaQuery()
                .eq(IndexBar::getCode, code)
                .orderByDesc(IndexBar::getTradeDate)
                .last("LIMIT " + Math.max(limit, 5)));
        Collections.reverse(desc);
        return desc;
    }

    private LocalDate latestDate(List<IndexBar> a, List<IndexBar> b, List<IndexBar> c) {
        LocalDate d = null;
        for (List<IndexBar> list : List.of(a, b, c)) {
            if (CollUtil.isEmpty(list)) {
                continue;
            }
            LocalDate t = list.get(list.size() - 1).getTradeDate();
            if (Objects.isNull(d) || (Objects.nonNull(t) && t.isAfter(d))) {
                d = t;
            }
        }
        return d;
    }

    private BigDecimal lastPct(List<IndexBar> bars) {
        if (CollUtil.isEmpty(bars)) {
            return null;
        }
        return bars.get(bars.size() - 1).getPctChg();
    }

    private Boolean aboveMa(List<IndexBar> bars, int n) {
        if (CollUtil.isEmpty(bars) || bars.size() < n) {
            return null;
        }
        BigDecimal close = bars.get(bars.size() - 1).getClosePrice();
        if (Objects.isNull(close)) {
            return null;
        }
        BigDecimal sum = ZERO;
        int cnt = 0;
        for (int i = bars.size() - n; i < bars.size(); i++) {
            BigDecimal c = bars.get(i).getClosePrice();
            if (Objects.isNull(c)) {
                continue;
            }
            sum = sum.add(c);
            cnt++;
        }
        if (cnt < n) {
            return null;
        }
        BigDecimal ma = sum.divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP);
        return close.compareTo(ma) >= 0;
    }

    private BigDecimal cumReturn(List<IndexBar> bars, int days) {
        if (CollUtil.isEmpty(bars) || bars.size() <= days) {
            return null;
        }
        BigDecimal end = bars.get(bars.size() - 1).getClosePrice();
        BigDecimal start = bars.get(bars.size() - 1 - days).getClosePrice();
        if (Objects.isNull(end) || Objects.isNull(start) || start.signum() == 0) {
            return null;
        }
        return end.subtract(start).divide(start, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal styleSpread(List<IndexBar> a, List<IndexBar> b, int days) {
        BigDecimal ra = cumReturn(a, days);
        BigDecimal rb = cumReturn(b, days);
        if (Objects.isNull(ra) || Objects.isNull(rb)) {
            return null;
        }
        return ra.subtract(rb).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 三市成交额序列：优先 amount，缺失则用 volume 近似做量能对比
     */
    private VolumeStat threeMarketAmountStat(List<IndexBar> sh, List<IndexBar> sz, List<IndexBar> bj, int maN) {
        Map<LocalDate, BigDecimal> amountByDate = new HashMap<>();
        Map<LocalDate, BigDecimal> volumeByDate = new HashMap<>();
        mergeMetric(amountByDate, sh, true);
        mergeMetric(amountByDate, sz, true);
        mergeMetric(amountByDate, bj, true);
        mergeMetric(volumeByDate, sh, false);
        mergeMetric(volumeByDate, sz, false);
        mergeMetric(volumeByDate, bj, false);

        boolean usedAmount = CollUtil.isNotEmpty(amountByDate);
        Map<LocalDate, BigDecimal> series = usedAmount ? amountByDate : volumeByDate;
        if (CollUtil.isEmpty(series)) {
            return null;
        }
        List<LocalDate> dates = new ArrayList<>(series.keySet());
        dates.sort(LocalDate::compareTo);
        if (dates.size() <= maN) {
            return null;
        }
        LocalDate lastDate = dates.get(dates.size() - 1);
        BigDecimal last = series.get(lastDate);
        if (Objects.isNull(last) || last.signum() <= 0) {
            return null;
        }
        BigDecimal sum = ZERO;
        int cnt = 0;
        for (int i = dates.size() - 1 - maN; i < dates.size() - 1; i++) {
            BigDecimal v = series.get(dates.get(i));
            if (Objects.isNull(v) || v.signum() <= 0) {
                continue;
            }
            sum = sum.add(v);
            cnt++;
        }
        if (cnt == 0) {
            return null;
        }
        BigDecimal ma = sum.divide(BigDecimal.valueOf(cnt), 6, RoundingMode.HALF_UP);
        BigDecimal vs = last.subtract(ma).divide(ma, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        String trend;
        if (vs.compareTo(new BigDecimal("8")) >= 0) {
            trend = "放量";
        } else if (vs.compareTo(new BigDecimal("-8")) <= 0) {
            trend = "缩量";
        } else {
            trend = "平量";
        }
        VolumeStat stat = new VolumeStat();
        stat.trend = trend;
        stat.vsMa5Pct = vs;
        // 展示用成交额：有 amount 用 amount，否则留给实时拉取
        stat.volume = usedAmount ? last : null;
        stat.usedAmount = usedAmount;
        return stat;
    }

    private void mergeMetric(Map<LocalDate, BigDecimal> target, List<IndexBar> bars, boolean useAmount) {
        if (CollUtil.isEmpty(bars)) {
            return;
        }
        for (IndexBar bar : bars) {
            if (Objects.isNull(bar) || Objects.isNull(bar.getTradeDate())) {
                continue;
            }
            BigDecimal v = useAmount ? bar.getAmount() : bar.getVolume();
            if (Objects.isNull(v) || v.signum() <= 0) {
                continue;
            }
            target.merge(bar.getTradeDate(), v, BigDecimal::add);
        }
    }

    /**
     * 实时三市成交额：上证 + 深成 + 北证50（元）
     */
    private BigDecimal fetchLiveThreeMarketAmount() {
        BigDecimal fromEm = fetchThreeMarketAmountFromEastMoney();
        if (Objects.nonNull(fromEm) && fromEm.signum() > 0) {
            return fromEm;
        }
        return fetchThreeMarketAmountFromSina();
    }

    private BigDecimal fetchThreeMarketAmountFromEastMoney() {
        // ulist 的 f48 对指数常为 "-"；stock/get + push2delay 更稳
        String[] secids = {"1.000001", "0.399001", "0.899050"};
        String[] hosts = {
                "https://push2delay.eastmoney.com",
                "https://push2.eastmoney.com"
        };
        BigDecimal sum = ZERO;
        int hit = 0;
        for (String secid : secids) {
            BigDecimal amount = null;
            for (String host : hosts) {
                amount = fetchIndexAmount(host, secid);
                if (Objects.nonNull(amount) && amount.signum() > 0) {
                    break;
                }
            }
            if (Objects.nonNull(amount) && amount.signum() > 0) {
                sum = sum.add(amount);
                hit++;
            }
        }
        return hit > 0 ? sum : null;
    }

    private BigDecimal fetchIndexAmount(String host, String secid) {
        String url = host + "/api/qt/stock/get?fltt=2&invt=2&secid=" + secid + "&fields=f48,f57,f58";
        try (HttpResponse response = HttpRequest.get(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://quote.eastmoney.com/")
                .header("Accept", "application/json,text/plain,*/*")
                .timeout(5000)
                .execute()) {
            if (!response.isOk()) {
                return null;
            }
            JSONObject root = JSONUtil.parseObj(response.body());
            JSONObject data = root.getJSONObject("data");
            if (Objects.isNull(data)) {
                return null;
            }
            Object raw = data.get("f48");
            if (Objects.isNull(raw) || "-".equals(String.valueOf(raw))) {
                return null;
            }
            return data.getBigDecimal("f48");
        } catch (Exception ex) {
            log.debug("东财指数成交额失败 secid={}: {}", secid, ex.getMessage());
            return null;
        }
    }

    private BigDecimal fetchThreeMarketAmountFromSina() {
        // hq 字段：index9=成交量，index10=成交额
        String url = "https://hq.sinajs.cn/list=sh000001,sz399001,bj899050";
        try (HttpResponse response = HttpRequest.get(url)
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://finance.sina.com.cn/")
                .timeout(5000)
                .execute()) {
            if (!response.isOk() || StringUtils.isBlank(response.body())) {
                return null;
            }
            BigDecimal sum = ZERO;
            int hit = 0;
            String[] lines = response.body().split(";");
            for (String line : lines) {
                int q1 = line.indexOf('"');
                int q2 = line.lastIndexOf('"');
                if (q1 < 0 || q2 <= q1) {
                    continue;
                }
                String[] parts = line.substring(q1 + 1, q2).split(",");
                if (parts.length < 10) {
                    continue;
                }
                try {
                    BigDecimal amount = new BigDecimal(parts[9].trim());
                    if (amount.signum() > 0) {
                        sum = sum.add(amount);
                        hit++;
                    }
                } catch (Exception ignored) {
                    // skip
                }
            }
            return hit > 0 ? sum : null;
        } catch (Exception ex) {
            log.debug("新浪三市成交额失败: {}", ex.getMessage());
            return null;
        }
    }

    private Integer countLimitDown() {
        try {
            Long cnt = stockBasicMapper.selectCount(Wrappers.<StockBasic>lambdaQuery()
                    .isNotNull(StockBasic::getPctChg)
                    .le(StockBasic::getPctChg, new BigDecimal("-9.5")));
            return Objects.nonNull(cnt) ? cnt.intValue() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private String formatAmount(BigDecimal amount) {
        if (Objects.isNull(amount) || amount.signum() <= 0) {
            return null;
        }
        BigDecimal wanYi = new BigDecimal("1000000000000");
        BigDecimal yi = new BigDecimal("100000000");
        BigDecimal wan = new BigDecimal("10000");
        if (amount.compareTo(wanYi) >= 0) {
            return amount.divide(wanYi, 2, RoundingMode.HALF_UP).toPlainString() + "万亿";
        }
        if (amount.compareTo(yi) >= 0) {
            return amount.divide(yi, 0, RoundingMode.HALF_UP).toPlainString() + "亿";
        }
        if (amount.compareTo(wan) >= 0) {
            return amount.divide(wan, 1, RoundingMode.HALF_UP).toPlainString() + "万";
        }
        return amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private LimitUpStat limitUpStat(LocalDate preferDate) {
        LocalDate day = preferDate;
        if (Objects.isNull(day)) {
            LimitUpPool latest = limitUpPoolMapper.selectOne(Wrappers.<LimitUpPool>lambdaQuery()
                    .orderByDesc(LimitUpPool::getTradeDate)
                    .last("LIMIT 1"));
            if (Objects.isNull(latest)) {
                return null;
            }
            day = latest.getTradeDate();
        }
        List<LimitUpPool> rows = limitUpPoolMapper.selectList(Wrappers.<LimitUpPool>lambdaQuery()
                .eq(LimitUpPool::getTradeDate, day));
        if (CollUtil.isEmpty(rows) && Objects.nonNull(preferDate)) {
            LimitUpPool latest = limitUpPoolMapper.selectOne(Wrappers.<LimitUpPool>lambdaQuery()
                    .orderByDesc(LimitUpPool::getTradeDate)
                    .last("LIMIT 1"));
            if (Objects.isNull(latest)) {
                return null;
            }
            day = latest.getTradeDate();
            rows = limitUpPoolMapper.selectList(Wrappers.<LimitUpPool>lambdaQuery()
                    .eq(LimitUpPool::getTradeDate, day));
        }
        if (CollUtil.isEmpty(rows)) {
            return null;
        }
        int max = 1;
        for (LimitUpPool row : rows) {
            if (Objects.nonNull(row.getLianban()) && row.getLianban() > max) {
                max = row.getLianban();
            }
        }
        LimitUpStat stat = new LimitUpStat();
        stat.asOf = day;
        stat.count = rows.size();
        stat.maxLianban = max;
        return stat;
    }

    private List<String> hotThemes() {
        List<String> themes = new ArrayList<>();
        try {
            List<SectorBoardItem> mainline = sectorBoardService.mainline(null, 6);
            if (CollUtil.isNotEmpty(mainline)) {
                for (SectorBoardItem item : mainline) {
                    if (StringUtils.isNotBlank(item.getName())) {
                        themes.add(item.getName());
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("主线识别失败: {}", ex.getMessage());
        }
        if (CollUtil.isNotEmpty(themes)) {
            return themes;
        }
        // 退化：取题材/概念当日涨幅前几
        SectorQuote latest = sectorQuoteMapper.selectOne(Wrappers.<SectorQuote>lambdaQuery()
                .in(SectorQuote::getBoardType, List.of("THEME", "CONCEPT"))
                .orderByDesc(SectorQuote::getTradeDate)
                .last("LIMIT 1"));
        if (Objects.isNull(latest) || Objects.isNull(latest.getTradeDate())) {
            return themes;
        }
        List<SectorQuote> tops = sectorQuoteMapper.selectList(Wrappers.<SectorQuote>lambdaQuery()
                .eq(SectorQuote::getTradeDate, latest.getTradeDate())
                .in(SectorQuote::getBoardType, List.of("THEME", "CONCEPT"))
                .orderByDesc(SectorQuote::getPctChg)
                .last("LIMIT 8"));
        Map<String, Boolean> seen = new HashMap<>();
        for (SectorQuote q : tops) {
            if (StringUtils.isBlank(q.getName()) || seen.containsKey(q.getName())) {
                continue;
            }
            seen.put(q.getName(), Boolean.TRUE);
            themes.add(q.getName());
            if (themes.size() >= 5) {
                break;
            }
        }
        return themes;
    }

    private String lineOf(String label, List<IndexBar> bars) {
        if (CollUtil.isEmpty(bars)) {
            return label + " -";
        }
        IndexBar last = bars.get(bars.size() - 1);
        return label + " " + fmtPct(last.getPctChg())
                + (Objects.nonNull(last.getClosePrice())
                ? (" · " + last.getClosePrice().setScale(2, RoundingMode.HALF_UP))
                : "");
    }

    private MarketIndexItem indexItemOf(String label, List<IndexBar> bars) {
        if (CollUtil.isEmpty(bars)) {
            return MarketIndexItem.builder()
                    .name(label)
                    .direction("flat")
                    .build();
        }
        IndexBar last = bars.get(bars.size() - 1);
        BigDecimal pct = last.getPctChg();
        String direction = "flat";
        if (Objects.nonNull(pct)) {
            if (pct.compareTo(ZERO) > 0) {
                direction = "up";
            } else if (pct.compareTo(ZERO) < 0) {
                direction = "down";
            }
        }
        return MarketIndexItem.builder()
                .name(label)
                .pctChg(Objects.nonNull(pct) ? pct.setScale(2, RoundingMode.HALF_UP) : null)
                .close(Objects.nonNull(last.getClosePrice())
                        ? last.getClosePrice().setScale(2, RoundingMode.HALF_UP) : null)
                .direction(direction)
                .build();
    }

    private BigDecimal avgNonNull(BigDecimal a, BigDecimal b) {
        if (Objects.nonNull(a) && Objects.nonNull(b)) {
            return a.add(b).divide(new BigDecimal("2"), 4, RoundingMode.HALF_UP);
        }
        return Objects.nonNull(a) ? a : b;
    }

    private String fmtPct(BigDecimal pct) {
        if (Objects.isNull(pct)) {
            return "-";
        }
        String sign = pct.compareTo(ZERO) > 0 ? "+" : "";
        return sign + pct.setScale(2, RoundingMode.HALF_UP) + "%";
    }

    private MarketTipItem tip(String level, String text) {
        return MarketTipItem.builder().level(level).text(text).build();
    }

    private static final class VolumeStat {
        private String trend;
        private BigDecimal vsMa5Pct;
        /** 三市成交额（元），可能为空待实时补 */ private BigDecimal volume;
        private boolean usedAmount;
    }

    private static final class LimitUpStat {
        private LocalDate asOf;
        private int count;
        private int maxLianban;
    }
}
