package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.ValuationBriefResp;
import com.awe.apex.quant.domain.dto.ValuationDimensionResp;
import com.awe.apex.quant.domain.dto.ValuationResp;
import com.awe.apex.quant.domain.dto.ValuationScreenItemResp;
import com.awe.apex.quant.domain.entity.ObservePool;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.StockFinAbstract;
import com.awe.apex.quant.domain.entity.StockFinIndicator;
import com.awe.apex.quant.domain.entity.Watchlist;
import com.awe.apex.quant.market.MarketCodeUtils;
import com.awe.apex.quant.mapper.ObservePoolMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.StockFinAbstractMapper;
import com.awe.apex.quant.mapper.StockFinIndicatorMapper;
import com.awe.apex.quant.mapper.WatchlistMapper;
import com.awe.apex.quant.service.IValuationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 本地多维估值引擎：行业相对 PE/PB、PEG、简化内在价值、财务质量
 */
@Service
public class ValuationServiceImpl implements IValuationService {

    private static final BigDecimal DISCOUNT_RATE = new BigDecimal("0.09");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int PEER_LIMIT = 400;

    @Resource
    private StockBasicMapper stockBasicMapper;

    @Resource
    private StockFinAbstractMapper stockFinAbstractMapper;

    @Resource
    private StockFinIndicatorMapper stockFinIndicatorMapper;

    @Resource
    private WatchlistMapper watchlistMapper;

    @Resource
    private ObservePoolMapper observePoolMapper;

    /**
     * 个股完整估值
     *
     * @param code 证券代码
     * @return 估值结论
     */
    @Override
    public ValuationResp evaluate(String code) {
        String pure = MarketCodeUtils.normalizeCode(code);
        if (StringUtils.isBlank(pure)) {
            return unknownResp("", "证券代码为空");
        }
        StockBasic basic = stockBasicMapper.selectOne(
                new LambdaQueryWrapper<StockBasic>().eq(StockBasic::getCode, pure).last("LIMIT 1"));
        if (Objects.isNull(basic)) {
            return unknownResp(pure, "本地无该股票基础信息，请先同步行情");
        }
        Map<String, IndustryStats> industryCache = new HashMap<>();
        return buildValuation(basic, industryCache, latestAbstract(basic.getCode()), latestIndicator(basic.getCode()));
    }

    /**
     * 轻量估值摘要
     *
     * @param code 证券代码
     * @return 摘要
     */
    @Override
    public ValuationBriefResp brief(String code) {
        return toBrief(evaluate(code));
    }

    /**
     * 批量轻量估值
     *
     * @param codes 代码列表
     * @return code -> 摘要
     */
    @Override
    public Map<String, ValuationBriefResp> briefBatch(Collection<String> codes) {
        Map<String, ValuationBriefResp> out = new HashMap<>();
        if (CollUtil.isEmpty(codes)) {
            return out;
        }
        Set<String> pureCodes = new HashSet<>();
        for (String code : codes) {
            String pure = MarketCodeUtils.normalizeCode(code);
            if (StringUtils.isNotBlank(pure)) {
                pureCodes.add(pure);
            }
        }
        if (pureCodes.isEmpty()) {
            return out;
        }
        List<StockBasic> basics = stockBasicMapper.selectList(
                new LambdaQueryWrapper<StockBasic>().in(StockBasic::getCode, pureCodes));
        Map<String, StockFinAbstract> absMap = loadLatestAbstractMap(pureCodes);
        Map<String, StockFinIndicator> indMap = loadLatestIndicatorMap(pureCodes);
        Map<String, IndustryStats> industryCache = new HashMap<>();
        for (StockBasic basic : basics) {
            if (Objects.isNull(basic) || StringUtils.isBlank(basic.getCode())) {
                continue;
            }
            ValuationResp full = buildValuation(basic, industryCache,
                    absMap.get(basic.getCode()), indMap.get(basic.getCode()));
            out.put(basic.getCode(), toBrief(full));
        }
        for (String pure : pureCodes) {
            out.putIfAbsent(pure, toBrief(unknownResp(pure, "本地无基础信息")));
        }
        return out;
    }

    /**
     * 估值筛选
     *
     * @param universe market / watchlist / observe
     * @param limit    条数
     * @param level    档位过滤
     * @return 列表
     */
    @Override
    public List<ValuationScreenItemResp> screen(String universe, Integer limit, String level) {
        int cap = Objects.isNull(limit) || limit <= 0 ? 30 : Math.min(limit, 80);
        String uni = StringUtils.isBlank(universe) ? "market" : universe.trim().toLowerCase();
        List<String> codes = resolveUniverseCodes(uni, Math.max(cap * 4, 120));
        if (CollUtil.isEmpty(codes)) {
            return List.of();
        }
        Map<String, ValuationBriefResp> briefs = briefBatch(codes);
        String levelFilter = StringUtils.isBlank(level) ? null : level.trim().toUpperCase();
        List<ValuationScreenItemResp> rows = new ArrayList<>();
        Map<String, StockBasic> basicMap = loadBasicMap(codes);
        for (String code : codes) {
            ValuationBriefResp brief = briefs.get(code);
            if (Objects.isNull(brief) || "UNKNOWN".equals(brief.getLevel())) {
                continue;
            }
            if (StringUtils.isNotBlank(levelFilter) && !levelFilter.equals(brief.getLevel())) {
                continue;
            }
            StockBasic basic = basicMap.get(code);
            rows.add(ValuationScreenItemResp.builder()
                    .code(code)
                    .name(Objects.nonNull(basic) ? basic.getName() : null)
                    .industry(Objects.nonNull(basic) ? basic.getIndustry() : null)
                    .latestPrice(Objects.nonNull(basic) ? basic.getLatestPrice() : null)
                    .peTtm(brief.getPeTtm())
                    .pb(brief.getPb())
                    .score(brief.getScore())
                    .level(brief.getLevel())
                    .levelLabel(brief.getLevelLabel())
                    .pePercentile(brief.getPePercentile())
                    .peg(brief.getPeg())
                    .marginOfSafety(brief.getMarginOfSafety())
                    .summary(brief.getSummary())
                    .build());
        }
        rows.sort(Comparator
                .comparing((ValuationScreenItemResp r) -> Objects.nonNull(r.getScore()) ? r.getScore() : BigDecimal.ZERO)
                .reversed());
        if (rows.size() > cap) {
            return rows.subList(0, cap);
        }
        return rows;
    }

    private ValuationResp buildValuation(StockBasic basic, Map<String, IndustryStats> industryCache,
                                         StockFinAbstract abs, StockFinIndicator ind) {
        String code = basic.getCode();

        BigDecimal pe = basic.getPeTtm();
        BigDecimal pb = basic.getPb();
        BigDecimal price = basic.getLatestPrice();
        BigDecimal roe = firstNonNull(
                Objects.nonNull(ind) ? ind.getRoe() : null,
                Objects.nonNull(abs) ? abs.getRoe() : null);
        BigDecimal debt = firstNonNull(
                Objects.nonNull(ind) ? ind.getDebtRatio() : null,
                Objects.nonNull(abs) ? abs.getDebtRatio() : null);
        BigDecimal netMargin = firstNonNull(
                Objects.nonNull(ind) ? ind.getNetMargin() : null,
                Objects.nonNull(abs) ? abs.getNetMargin() : null);
        BigDecimal eps = firstNonNull(
                Objects.nonNull(ind) ? ind.getEps() : null,
                Objects.nonNull(abs) ? abs.getEpsBasic() : null);
        BigDecimal bps = firstNonNull(
                Objects.nonNull(ind) ? ind.getBps() : null,
                Objects.nonNull(abs) ? abs.getBps() : null);
        BigDecimal revenueYoy = Objects.nonNull(abs) ? abs.getRevenueYoy() : null;
        BigDecimal profitYoy = Objects.nonNull(abs) ? abs.getNetProfitYoy() : null;
        LocalDate reportDate = firstNonNullDate(
                Objects.nonNull(ind) ? ind.getReportDate() : null,
                Objects.nonNull(abs) ? abs.getReportDate() : null);

        IndustryStats stats = industryStats(basic.getIndustry(), industryCache);
        BigDecimal pePct = percentile(pe, stats.peList);
        BigDecimal pbPct = percentile(pb, stats.pbList);

        BigDecimal growthPct = pickGrowth(profitYoy, revenueYoy);
        BigDecimal peg = null;
        if (Objects.nonNull(pe) && pe.compareTo(BigDecimal.ZERO) > 0
                && Objects.nonNull(growthPct) && growthPct.compareTo(BigDecimal.ONE) > 0) {
            peg = pe.divide(growthPct, 2, RoundingMode.HALF_UP);
        }

        BigDecimal earningsYield = null;
        if (Objects.nonNull(pe) && pe.compareTo(BigDecimal.ZERO) > 0) {
            earningsYield = HUNDRED.divide(pe, 2, RoundingMode.HALF_UP);
        }

        // 合理 PE/PB：增长 + ROE + 行业中位锚，避免品牌股被机械压到净资产附近
        BigDecimal fairPe = calcFairPe(growthPct, roe, netMargin, stats.peMedian);
        BigDecimal fairPb = calcFairPb(roe, netMargin, stats.pbMedian);
        BigDecimal fairMid = calcFairPriceMid(price, pe, pb, eps, bps, fairPe, fairPb, stats);
        BigDecimal fairLow = Objects.nonNull(fairMid) ? fairMid.multiply(new BigDecimal("0.85")).setScale(2, RoundingMode.HALF_UP) : null;
        BigDecimal fairHigh = Objects.nonNull(fairMid) ? fairMid.multiply(new BigDecimal("1.15")).setScale(2, RoundingMode.HALF_UP) : null;
        BigDecimal margin = null;
        if (Objects.nonNull(fairMid) && fairMid.compareTo(BigDecimal.ZERO) > 0 && Objects.nonNull(price)
                && price.compareTo(BigDecimal.ZERO) > 0) {
            margin = fairMid.subtract(price).multiply(HUNDRED).divide(fairMid, 2, RoundingMode.HALF_UP);
            // 极端偏离时钳制展示，避免简化模型对品牌溢价失真成几百%溢价
            if (margin.compareTo(new BigDecimal("-80")) < 0) {
                margin = new BigDecimal("-80.00");
            } else if (margin.compareTo(new BigDecimal("80")) > 0) {
                margin = new BigDecimal("80.00");
            }
        }

        List<ValuationDimensionResp> dims = new ArrayList<>();
        dims.add(dimPeRelative(pe, pePct, stats));
        dims.add(dimPbRelative(pb, pbPct, stats));
        dims.add(dimPeg(peg, growthPct, pe));
        dims.add(dimDcf(margin, fairMid, price, pe, fairPe));
        dims.add(dimQuality(roe, debt, netMargin));
        dims.add(dimGrowth(growthPct, profitYoy, revenueYoy));

        BigDecimal score = weightedScore(dims);
        LevelBand band = levelOf(score, pe, pb);
        List<String> bulls = new ArrayList<>();
        List<String> bears = new ArrayList<>();
        fillPoints(bulls, bears, pe, pb, pePct, pbPct, peg, margin, roe, debt, growthPct, stats);

        String summary = buildSummary(basic.getName(), band, pe, pePct, peg, margin, stats.peerCount);
        String action = actionHint(band);
        List<String> assumptions = List.of(
                "折现率约 9%，用于简化内在价值区间，非精确 DCF",
                "增长取净利润同比与营收同比中较稳健者，封顶 25%",
                "行业分位基于本地同行业有效 PE/PB 样本（最多 " + PEER_LIMIT + " 只）",
                "无一致预期时不做前瞻 PE；结论仅供研究参考"
        );
        String dataNote = buildDataNote(abs, ind, pe, pb, stats.peerCount);

        return ValuationResp.builder()
                .code(code)
                .name(basic.getName())
                .industry(basic.getIndustry())
                .latestPrice(price)
                .pctChg(basic.getPctChg())
                .peTtm(pe)
                .pb(pb)
                .totalMv(basic.getTotalMv())
                .circMv(basic.getCircMv())
                .reportDate(reportDate)
                .roe(roe)
                .debtRatio(debt)
                .netMargin(netMargin)
                .eps(eps)
                .bps(bps)
                .revenueYoy(revenueYoy)
                .netProfitYoy(profitYoy)
                .level(band.level)
                .levelLabel(band.label)
                .score(score)
                .summary(summary)
                .actionHint(action)
                .industryPeMedian(stats.peMedian)
                .industryPbMedian(stats.pbMedian)
                .pePercentile(pePct)
                .pbPercentile(pbPct)
                .industryPeerCount(stats.peerCount)
                .peg(peg)
                .earningsYield(earningsYield)
                .fairPe(fairPe)
                .fairPb(fairPb)
                .fairPriceLow(fairLow)
                .fairPriceMid(fairMid)
                .fairPriceHigh(fairHigh)
                .marginOfSafety(margin)
                .dimensions(dims)
                .bullPoints(bulls)
                .bearPoints(bears)
                .assumptions(assumptions)
                .dataNote(dataNote)
                .build();
    }

    private ValuationDimensionResp dimPeRelative(BigDecimal pe, BigDecimal pePct, IndustryStats stats) {
        BigDecimal score;
        String verdict;
        String detail;
        if (Objects.isNull(pe) || pe.compareTo(BigDecimal.ZERO) <= 0) {
            score = new BigDecimal("45");
            verdict = "PE 不可用";
            detail = "亏损或缺失 PE，行业相对估值弱化";
        } else if (Objects.isNull(pePct) || stats.peerCount < 8) {
            // 绝对带粗分
            if (pe.compareTo(new BigDecimal("15")) <= 0) {
                score = new BigDecimal("78");
                verdict = "绝对 PE 偏低";
            } else if (pe.compareTo(new BigDecimal("30")) <= 0) {
                score = new BigDecimal("55");
                verdict = "绝对 PE 中性";
            } else if (pe.compareTo(new BigDecimal("50")) <= 0) {
                score = new BigDecimal("35");
                verdict = "绝对 PE 偏高";
            } else {
                score = new BigDecimal("22");
                verdict = "绝对 PE 很高";
            }
            detail = "PE " + pe.setScale(2, RoundingMode.HALF_UP)
                    + "；同业样本不足，改用绝对区间";
        } else {
            // 分位越低越便宜
            score = HUNDRED.subtract(pePct).max(new BigDecimal("5")).min(new BigDecimal("95"));
            if (pePct.compareTo(new BigDecimal("30")) <= 0) {
                verdict = "相对同行便宜";
            } else if (pePct.compareTo(new BigDecimal("60")) <= 0) {
                verdict = "相对同行中性";
            } else {
                verdict = "相对同行偏贵";
            }
            detail = "PE " + pe.setScale(2, RoundingMode.HALF_UP)
                    + " · 行业中位 " + fmt(stats.peMedian)
                    + " · 分位 " + pePct.setScale(1, RoundingMode.HALF_UP) + "%（样本 " + stats.peerCount + "）";
        }
        return ValuationDimensionResp.builder()
                .key("peRelative")
                .name("PE 相对估值")
                .score(score.setScale(1, RoundingMode.HALF_UP))
                .weight(new BigDecimal("0.25"))
                .verdict(verdict)
                .detail(detail)
                .rawValue(pe)
                .build();
    }

    private ValuationDimensionResp dimPbRelative(BigDecimal pb, BigDecimal pbPct, IndustryStats stats) {
        BigDecimal score;
        String verdict;
        String detail;
        if (Objects.isNull(pb) || pb.compareTo(BigDecimal.ZERO) <= 0) {
            score = new BigDecimal("45");
            verdict = "PB 不可用";
            detail = "缺失 PB";
        } else if (Objects.isNull(pbPct) || stats.peerCount < 8) {
            if (pb.compareTo(new BigDecimal("1.2")) <= 0) {
                score = new BigDecimal("80");
                verdict = "绝对 PB 偏低";
            } else if (pb.compareTo(new BigDecimal("3")) <= 0) {
                score = new BigDecimal("55");
                verdict = "绝对 PB 中性";
            } else if (pb.compareTo(new BigDecimal("6")) <= 0) {
                score = new BigDecimal("35");
                verdict = "绝对 PB 偏高";
            } else {
                score = new BigDecimal("22");
                verdict = "绝对 PB 很高";
            }
            detail = "PB " + pb.setScale(2, RoundingMode.HALF_UP) + "；同业样本不足";
        } else {
            score = HUNDRED.subtract(pbPct).max(new BigDecimal("5")).min(new BigDecimal("95"));
            if (pbPct.compareTo(new BigDecimal("30")) <= 0) {
                verdict = "净值溢价偏低";
            } else if (pbPct.compareTo(new BigDecimal("60")) <= 0) {
                verdict = "净值溢价中性";
            } else {
                verdict = "净值溢价偏高";
            }
            detail = "PB " + pb.setScale(2, RoundingMode.HALF_UP)
                    + " · 行业中位 " + fmt(stats.pbMedian)
                    + " · 分位 " + pbPct.setScale(1, RoundingMode.HALF_UP) + "%";
        }
        return ValuationDimensionResp.builder()
                .key("pbRelative")
                .name("PB 相对估值")
                .score(score.setScale(1, RoundingMode.HALF_UP))
                .weight(new BigDecimal("0.15"))
                .verdict(verdict)
                .detail(detail)
                .rawValue(pb)
                .build();
    }

    private ValuationDimensionResp dimPeg(BigDecimal peg, BigDecimal growthPct, BigDecimal pe) {
        BigDecimal score;
        String verdict;
        String detail;
        // 低增速时 PEG 失真，弱化该维
        if (Objects.nonNull(growthPct) && growthPct.compareTo(new BigDecimal("3")) < 0
                && growthPct.compareTo(BigDecimal.ZERO) >= 0) {
            score = new BigDecimal("46");
            verdict = "低增速·PEG参考弱";
            detail = "增长 " + fmt(growthPct) + "%，PEG 不宜单独决策"
                    + (Objects.nonNull(peg) ? "（名义 PEG " + peg + "）" : "");
            return ValuationDimensionResp.builder()
                    .key("peg")
                    .name("PEG 成长估值")
                    .score(score)
                    .weight(new BigDecimal("0.10"))
                    .verdict(verdict)
                    .detail(detail)
                    .rawValue(peg)
                    .build();
        }
        if (Objects.isNull(peg)) {
            score = new BigDecimal("48");
            verdict = "PEG 不可算";
            detail = Objects.isNull(growthPct) || growthPct.compareTo(BigDecimal.ONE) <= 0
                    ? "增长缺失或过低，PEG 参考价值弱"
                    : "PE 无效";
        } else if (peg.compareTo(new BigDecimal("0.8")) <= 0) {
            score = new BigDecimal("88");
            verdict = "成长性价比高";
            detail = "PEG " + peg + "（PE/增长）";
        } else if (peg.compareTo(new BigDecimal("1.2")) <= 0) {
            score = new BigDecimal("70");
            verdict = "成长定价合理";
            detail = "PEG " + peg;
        } else if (peg.compareTo(new BigDecimal("1.8")) <= 0) {
            score = new BigDecimal("48");
            verdict = "成长定价偏紧";
            detail = "PEG " + peg;
        } else {
            score = new BigDecimal("28");
            verdict = "增长难撑估值";
            detail = "PEG " + peg + (Objects.nonNull(pe) ? " · PE " + pe.setScale(1, RoundingMode.HALF_UP) : "");
        }
        return ValuationDimensionResp.builder()
                .key("peg")
                .name("PEG 成长估值")
                .score(score)
                .weight(new BigDecimal("0.18"))
                .verdict(verdict)
                .detail(detail)
                .rawValue(peg)
                .build();
    }

    private ValuationDimensionResp dimDcf(BigDecimal margin, BigDecimal fairMid, BigDecimal price,
                                          BigDecimal pe, BigDecimal fairPe) {
        BigDecimal score;
        String verdict;
        String detail;
        if (Objects.isNull(margin) || Objects.isNull(fairMid)) {
            score = new BigDecimal("50");
            verdict = "内在价值弱信号";
            detail = "EPS/BPS 或估值倍数不足，跳过安全边际";
        } else if (margin.compareTo(new BigDecimal("25")) >= 0) {
            score = new BigDecimal("90");
            verdict = "安全边际充足";
            detail = "中枢价 " + fairMid + " vs 现价 " + fmt(price) + " · 边际 " + margin + "%";
        } else if (margin.compareTo(new BigDecimal("10")) >= 0) {
            score = new BigDecimal("72");
            verdict = "略有安全边际";
            detail = "中枢价 " + fairMid + " · 边际 " + margin + "%";
        } else if (margin.compareTo(new BigDecimal("-10")) >= 0) {
            score = new BigDecimal("52");
            verdict = "贴近公允价值";
            detail = "中枢价 " + fairMid + " · 边际 " + margin + "%";
        } else if (margin.compareTo(new BigDecimal("-25")) >= 0) {
            score = new BigDecimal("32");
            verdict = "高于内在中枢";
            detail = "中枢价 " + fairMid + " · 溢价 " + margin.abs() + "%";
        } else {
            // 相对 PE 仍接近公允时，不完全按极端安全边际打分（品牌溢价常见）
            if (Objects.nonNull(pe) && Objects.nonNull(fairPe) && fairPe.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal peGap = pe.subtract(fairPe).abs().divide(fairPe, 4, RoundingMode.HALF_UP);
                if (peGap.compareTo(new BigDecimal("0.35")) <= 0) {
                    score = new BigDecimal("40");
                    verdict = "PE接近公允·价格含品牌溢价";
                    detail = "中枢 " + fairMid + " · PE " + pe.setScale(1, RoundingMode.HALF_UP)
                            + " vs 公允PE " + fairPe + "（模型对品牌溢价仅作参考）";
                } else {
                    score = new BigDecimal("22");
                    verdict = "显著溢价";
                    detail = "中枢价 " + fairMid + " · 溢价约 " + margin.abs() + "%";
                }
            } else {
                score = new BigDecimal("22");
                verdict = "显著溢价";
                detail = "中枢价 " + fairMid + " · 溢价约 " + margin.abs() + "%";
            }
        }
        return ValuationDimensionResp.builder()
                .key("dcf")
                .name("简化内在价值")
                .score(score)
                .weight(new BigDecimal("0.22"))
                .verdict(verdict)
                .detail(detail)
                .rawValue(margin)
                .build();
    }

    private ValuationDimensionResp dimQuality(BigDecimal roe, BigDecimal debt, BigDecimal netMargin) {
        BigDecimal score = new BigDecimal("50");
        List<String> bits = new ArrayList<>();
        if (Objects.nonNull(roe)) {
            if (roe.compareTo(new BigDecimal("18")) >= 0) {
                score = score.add(new BigDecimal("22"));
                bits.add("ROE 优秀");
            } else if (roe.compareTo(new BigDecimal("12")) >= 0) {
                score = score.add(new BigDecimal("12"));
                bits.add("ROE 良好");
            } else if (roe.compareTo(new BigDecimal("8")) >= 0) {
                score = score.add(new BigDecimal("2"));
                bits.add("ROE 一般");
            } else {
                score = score.subtract(new BigDecimal("15"));
                bits.add("ROE 偏弱");
            }
        } else {
            bits.add("ROE 缺失");
        }
        if (Objects.nonNull(debt)) {
            if (debt.compareTo(new BigDecimal("70")) >= 0) {
                score = score.subtract(new BigDecimal("18"));
                bits.add("负债偏高");
            } else if (debt.compareTo(new BigDecimal("55")) >= 0) {
                score = score.subtract(new BigDecimal("6"));
                bits.add("负债中等偏高");
            } else if (debt.compareTo(new BigDecimal("40")) <= 0) {
                score = score.add(new BigDecimal("8"));
                bits.add("负债稳健");
            }
        }
        if (Objects.nonNull(netMargin)) {
            if (netMargin.compareTo(new BigDecimal("15")) >= 0) {
                score = score.add(new BigDecimal("10"));
                bits.add("净利率厚");
            } else if (netMargin.compareTo(new BigDecimal("5")) < 0) {
                score = score.subtract(new BigDecimal("8"));
                bits.add("净利率薄");
            }
        }
        score = score.max(new BigDecimal("8")).min(new BigDecimal("95")).setScale(1, RoundingMode.HALF_UP);
        String verdict;
        if (score.compareTo(new BigDecimal("70")) >= 0) {
            verdict = "财务质量较好";
        } else if (score.compareTo(new BigDecimal("45")) >= 0) {
            verdict = "财务质量中性";
        } else {
            verdict = "财务质量偏弱";
        }
        return ValuationDimensionResp.builder()
                .key("quality")
                .name("财务质量")
                .score(score)
                .weight(new BigDecimal("0.12"))
                .verdict(verdict)
                .detail(String.join(" · ", bits))
                .rawValue(roe)
                .build();
    }

    private ValuationDimensionResp dimGrowth(BigDecimal growthPct, BigDecimal profitYoy, BigDecimal revenueYoy) {
        BigDecimal score;
        String verdict;
        if (Objects.isNull(growthPct)) {
            score = new BigDecimal("45");
            verdict = "增长数据缺失";
        } else if (growthPct.compareTo(new BigDecimal("25")) >= 0) {
            score = new BigDecimal("82");
            verdict = "高增长";
        } else if (growthPct.compareTo(new BigDecimal("12")) >= 0) {
            score = new BigDecimal("68");
            verdict = "稳健增长";
        } else if (growthPct.compareTo(new BigDecimal("3")) >= 0) {
            score = new BigDecimal("52");
            verdict = "低速增长";
        } else if (growthPct.compareTo(BigDecimal.ZERO) >= 0) {
            score = new BigDecimal("40");
            verdict = "近乎停滞";
        } else {
            score = new BigDecimal("22");
            verdict = "盈利下滑";
        }
        String detail = "采用增长 " + fmt(growthPct) + "%"
                + (Objects.nonNull(profitYoy) ? " · 净利同比 " + fmt(profitYoy) + "%" : "")
                + (Objects.nonNull(revenueYoy) ? " · 营收同比 " + fmt(revenueYoy) + "%" : "");
        return ValuationDimensionResp.builder()
                .key("growth")
                .name("成长性")
                .score(score)
                .weight(new BigDecimal("0.08"))
                .verdict(verdict)
                .detail(detail)
                .rawValue(growthPct)
                .build();
    }

    private BigDecimal weightedScore(List<ValuationDimensionResp> dims) {
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal wSum = BigDecimal.ZERO;
        for (ValuationDimensionResp d : dims) {
            if (Objects.isNull(d) || Objects.isNull(d.getScore()) || Objects.isNull(d.getWeight())) {
                continue;
            }
            sum = sum.add(d.getScore().multiply(d.getWeight()));
            wSum = wSum.add(d.getWeight());
        }
        if (wSum.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal("50.0");
        }
        return sum.divide(wSum, 1, RoundingMode.HALF_UP);
    }

    private LevelBand levelOf(BigDecimal score, BigDecimal pe, BigDecimal pb) {
        boolean noVal = (Objects.isNull(pe) || pe.compareTo(BigDecimal.ZERO) <= 0)
                && (Objects.isNull(pb) || pb.compareTo(BigDecimal.ZERO) <= 0);
        if (noVal) {
            return new LevelBand("UNKNOWN", "数据不足");
        }
        if (score.compareTo(new BigDecimal("75")) >= 0) {
            return new LevelBand("UNDERVALUED", "明显低估");
        }
        if (score.compareTo(new BigDecimal("62")) >= 0) {
            return new LevelBand("SLIGHTLY_CHEAP", "偏低");
        }
        if (score.compareTo(new BigDecimal("45")) >= 0) {
            return new LevelBand("FAIR", "合理");
        }
        if (score.compareTo(new BigDecimal("32")) >= 0) {
            return new LevelBand("SLIGHTLY_EXPENSIVE", "偏高");
        }
        return new LevelBand("OVERVALUED", "明显高估");
    }

    private String actionHint(LevelBand band) {
        return switch (band.level) {
            case "UNDERVALUED" -> "积极观察 · 估值有吸引力，仍需等买点";
            case "SLIGHTLY_CHEAP" -> "可关注 · 估值不贵，结合技术位";
            case "FAIR" -> "中性 · 估值中性，勿只因便宜或贵决策";
            case "SLIGHTLY_EXPENSIVE" -> "谨慎 · 估值偏贵，避免追高";
            case "OVERVALUED" -> "回避追高 · 估值压力大，降权或只做情绪观察";
            default -> "数据不足 · 先补基本面/行情后再评";
        };
    }

    private String buildSummary(String name, LevelBand band, BigDecimal pe, BigDecimal pePct,
                                BigDecimal peg, BigDecimal margin, int peerCount) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(name)) {
            sb.append(name).append("：");
        }
        sb.append(band.label);
        if (Objects.nonNull(pe) && pe.compareTo(BigDecimal.ZERO) > 0) {
            sb.append(" · PE ").append(pe.setScale(1, RoundingMode.HALF_UP));
        }
        if (Objects.nonNull(pePct) && peerCount >= 8) {
            sb.append("（行业分位 ").append(pePct.setScale(0, RoundingMode.HALF_UP)).append("%）");
        }
        if (Objects.nonNull(peg)) {
            sb.append(" · PEG ").append(peg);
        }
        if (Objects.nonNull(margin)) {
            sb.append(" · 安全边际 ").append(margin).append("%");
        }
        return sb.toString();
    }

    private void fillPoints(List<String> bulls, List<String> bears,
                            BigDecimal pe, BigDecimal pb, BigDecimal pePct, BigDecimal pbPct,
                            BigDecimal peg, BigDecimal margin, BigDecimal roe, BigDecimal debt,
                            BigDecimal growthPct, IndustryStats stats) {
        if (Objects.nonNull(pePct) && pePct.compareTo(new BigDecimal("30")) <= 0 && stats.peerCount >= 8) {
            bulls.add("PE 处于行业较低分位");
        } else if (Objects.nonNull(pePct) && pePct.compareTo(new BigDecimal("70")) >= 0 && stats.peerCount >= 8) {
            bears.add("PE 高于行业多数同行");
        }
        if (Objects.nonNull(pbPct) && pbPct.compareTo(new BigDecimal("30")) <= 0 && stats.peerCount >= 8) {
            bulls.add("PB 相对行业便宜");
        } else if (Objects.nonNull(pbPct) && pbPct.compareTo(new BigDecimal("70")) >= 0) {
            bears.add("PB 溢价偏高");
        }
        if (Objects.nonNull(peg) && peg.compareTo(new BigDecimal("1")) <= 0) {
            bulls.add("PEG≤1，增长对估值有支撑");
        } else if (Objects.nonNull(peg) && peg.compareTo(new BigDecimal("2")) >= 0) {
            bears.add("PEG 偏高，增长难覆盖估值");
        }
        if (Objects.nonNull(margin) && margin.compareTo(new BigDecimal("15")) >= 0) {
            bulls.add("相对简化内在价值有安全边际");
        } else if (Objects.nonNull(margin) && margin.compareTo(new BigDecimal("-15")) <= 0) {
            bears.add("现价高于简化内在价值中枢");
        }
        if (Objects.nonNull(roe) && roe.compareTo(new BigDecimal("15")) >= 0) {
            bulls.add("ROE 具备一定盈利能力");
        } else if (Objects.nonNull(roe) && roe.compareTo(new BigDecimal("6")) < 0) {
            bears.add("ROE 偏弱，便宜也可能是价值陷阱");
        }
        if (Objects.nonNull(debt) && debt.compareTo(new BigDecimal("70")) >= 0) {
            bears.add("资产负债率偏高，折价需谨慎");
        }
        if (Objects.nonNull(growthPct) && growthPct.compareTo(new BigDecimal("15")) >= 0) {
            bulls.add("近期增长较快");
        } else if (Objects.nonNull(growthPct) && growthPct.compareTo(BigDecimal.ZERO) < 0) {
            bears.add("盈利同比下滑");
        }
        if (Objects.nonNull(pe) && pe.compareTo(new BigDecimal("60")) > 0) {
            bears.add("绝对 PE 很高，预期已充分");
        }
        if (bulls.isEmpty()) {
            bulls.add("暂无突出估值优势，需看技术与事件催化");
        }
        if (bears.isEmpty()) {
            bears.add("未见突出估值风险，仍需结合仓位与市场环境");
        }
    }

    private BigDecimal calcFairPe(BigDecimal growthPct, BigDecimal roe, BigDecimal netMargin, BigDecimal industryPeMedian) {
        BigDecimal g = Objects.nonNull(growthPct)
                ? growthPct.max(BigDecimal.ZERO).min(new BigDecimal("25"))
                : new BigDecimal("8");
        // 基准 12 + 增长×0.7，ROE/净利率微调
        BigDecimal fair = new BigDecimal("12").add(g.multiply(new BigDecimal("0.70")));
        if (Objects.nonNull(roe)) {
            if (roe.compareTo(new BigDecimal("18")) >= 0) {
                fair = fair.add(new BigDecimal("4"));
            } else if (roe.compareTo(new BigDecimal("8")) < 0) {
                fair = fair.subtract(new BigDecimal("3"));
            }
        }
        if (Objects.nonNull(netMargin) && netMargin.compareTo(new BigDecimal("25")) >= 0) {
            fair = fair.add(new BigDecimal("6"));
        } else if (Objects.nonNull(netMargin) && netMargin.compareTo(new BigDecimal("15")) >= 0) {
            fair = fair.add(new BigDecimal("3"));
        }
        // 与行业中位折中，避免孤立绝对带
        if (Objects.nonNull(industryPeMedian) && industryPeMedian.compareTo(BigDecimal.ZERO) > 0) {
            fair = fair.multiply(new BigDecimal("0.55"))
                    .add(industryPeMedian.multiply(new BigDecimal("0.45")));
        }
        return fair.max(new BigDecimal("8")).min(new BigDecimal("55")).setScale(1, RoundingMode.HALF_UP);
    }

    private BigDecimal calcFairPb(BigDecimal roe, BigDecimal netMargin, BigDecimal industryPbMedian) {
        BigDecimal fair;
        if (Objects.isNull(roe) || roe.compareTo(BigDecimal.ZERO) <= 0) {
            fair = new BigDecimal("1.50");
        } else {
            // 粗略：公允 PB ≈ ROE% / r% ，r=9
            fair = roe.divide(DISCOUNT_RATE.multiply(HUNDRED), 2, RoundingMode.HALF_UP);
        }
        if (Objects.nonNull(netMargin) && netMargin.compareTo(new BigDecimal("30")) >= 0) {
            fair = fair.multiply(new BigDecimal("1.8"));
        } else if (Objects.nonNull(netMargin) && netMargin.compareTo(new BigDecimal("15")) >= 0) {
            fair = fair.multiply(new BigDecimal("1.35"));
        }
        if (Objects.nonNull(industryPbMedian) && industryPbMedian.compareTo(BigDecimal.ZERO) > 0) {
            fair = fair.multiply(new BigDecimal("0.4"))
                    .add(industryPbMedian.multiply(new BigDecimal("0.6")));
        }
        return fair.max(new BigDecimal("0.6")).min(new BigDecimal("12")).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calcFairPriceMid(BigDecimal price, BigDecimal pe, BigDecimal pb,
                                        BigDecimal eps, BigDecimal bps,
                                        BigDecimal fairPe, BigDecimal fairPb, IndustryStats stats) {
        List<BigDecimal> candidates = new ArrayList<>();
        BigDecimal useEps = eps;
        if ((Objects.isNull(useEps) || useEps.compareTo(BigDecimal.ZERO) <= 0)
                && Objects.nonNull(price) && Objects.nonNull(pe) && pe.compareTo(BigDecimal.ZERO) > 0) {
            useEps = price.divide(pe, 4, RoundingMode.HALF_UP);
        }
        // PE 锚权重更高（品牌股 PB 易失真）
        if (Objects.nonNull(useEps) && useEps.compareTo(BigDecimal.ZERO) > 0 && Objects.nonNull(fairPe)) {
            candidates.add(useEps.multiply(fairPe).setScale(2, RoundingMode.HALF_UP));
            candidates.add(useEps.multiply(fairPe).setScale(2, RoundingMode.HALF_UP));
        }
        BigDecimal useBps = bps;
        if ((Objects.isNull(useBps) || useBps.compareTo(BigDecimal.ZERO) <= 0)
                && Objects.nonNull(price) && Objects.nonNull(pb) && pb.compareTo(BigDecimal.ZERO) > 0) {
            useBps = price.divide(pb, 4, RoundingMode.HALF_UP);
        }
        if (Objects.nonNull(useBps) && useBps.compareTo(BigDecimal.ZERO) > 0 && Objects.nonNull(fairPb)) {
            candidates.add(useBps.multiply(fairPb).setScale(2, RoundingMode.HALF_UP));
        }
        // 盈利收益率锚：与 fairPe 折中
        if (Objects.nonNull(useEps) && useEps.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal eyPe = new BigDecimal("12.5");
            BigDecimal blendPe = fairPe.add(eyPe).divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
            candidates.add(useEps.multiply(blendPe).setScale(2, RoundingMode.HALF_UP));
        }
        // 行业 PE 中位锚
        if (Objects.nonNull(useEps) && useEps.compareTo(BigDecimal.ZERO) > 0
                && Objects.nonNull(stats) && Objects.nonNull(stats.peMedian)
                && stats.peMedian.compareTo(BigDecimal.ZERO) > 0) {
            candidates.add(useEps.multiply(stats.peMedian).setScale(2, RoundingMode.HALF_UP));
        }
        if (candidates.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal c : candidates) {
            sum = sum.add(c);
        }
        return sum.divide(BigDecimal.valueOf(candidates.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal pickGrowth(BigDecimal profitYoy, BigDecimal revenueYoy) {
        if (Objects.isNull(profitYoy) && Objects.isNull(revenueYoy)) {
            return null;
        }
        if (Objects.isNull(profitYoy)) {
            return revenueYoy;
        }
        if (Objects.isNull(revenueYoy)) {
            return profitYoy;
        }
        // 取较低者更稳健；若一边为负取利润侧
        if (profitYoy.compareTo(BigDecimal.ZERO) < 0 || revenueYoy.compareTo(BigDecimal.ZERO) < 0) {
            return profitYoy.min(revenueYoy);
        }
        return profitYoy.min(revenueYoy);
    }

    private IndustryStats industryStats(String industry, Map<String, IndustryStats> cache) {
        String key = StringUtils.isBlank(industry) ? "" : industry.trim();
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        IndustryStats empty = new IndustryStats();
        if (StringUtils.isBlank(key)) {
            cache.put(key, empty);
            return empty;
        }
        List<StockBasic> peers = stockBasicMapper.selectList(
                new LambdaQueryWrapper<StockBasic>()
                        .eq(StockBasic::getIndustry, key)
                        .and(w -> w.isNotNull(StockBasic::getPeTtm).or().isNotNull(StockBasic::getPb))
                        .last("LIMIT " + PEER_LIMIT));
        IndustryStats stats = new IndustryStats();
        stats.peerCount = peers.size();
        for (StockBasic p : peers) {
            if (Objects.nonNull(p.getPeTtm()) && p.getPeTtm().compareTo(BigDecimal.ZERO) > 0
                    && p.getPeTtm().compareTo(new BigDecimal("200")) < 0) {
                stats.peList.add(p.getPeTtm());
            }
            if (Objects.nonNull(p.getPb()) && p.getPb().compareTo(BigDecimal.ZERO) > 0
                    && p.getPb().compareTo(new BigDecimal("40")) < 0) {
                stats.pbList.add(p.getPb());
            }
        }
        stats.peMedian = median(stats.peList);
        stats.pbMedian = median(stats.pbList);
        cache.put(key, stats);
        return stats;
    }

    private BigDecimal percentile(BigDecimal value, List<BigDecimal> universe) {
        if (Objects.isNull(value) || CollUtil.isEmpty(universe) || universe.size() < 5) {
            return null;
        }
        int below = 0;
        for (BigDecimal v : universe) {
            if (v.compareTo(value) <= 0) {
                below++;
            }
        }
        return BigDecimal.valueOf(below * 100.0 / universe.size()).setScale(1, RoundingMode.HALF_UP);
    }

    private BigDecimal median(List<BigDecimal> values) {
        if (CollUtil.isEmpty(values)) {
            return null;
        }
        List<BigDecimal> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2).setScale(2, RoundingMode.HALF_UP);
        }
        return sorted.get(n / 2 - 1).add(sorted.get(n / 2))
                .divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
    }

    private StockFinAbstract latestAbstract(String code) {
        return stockFinAbstractMapper.selectOne(
                new LambdaQueryWrapper<StockFinAbstract>()
                        .eq(StockFinAbstract::getCode, code)
                        .orderByDesc(StockFinAbstract::getReportDate)
                        .last("LIMIT 1"));
    }

    private StockFinIndicator latestIndicator(String code) {
        return stockFinIndicatorMapper.selectOne(
                new LambdaQueryWrapper<StockFinIndicator>()
                        .eq(StockFinIndicator::getCode, code)
                        .orderByDesc(StockFinIndicator::getReportDate)
                        .last("LIMIT 1"));
    }

    /**
     * 批量取各代码最新一期财务摘要（按报告期降序扫一遍）
     */
    private Map<String, StockFinAbstract> loadLatestAbstractMap(Set<String> codes) {
        Map<String, StockFinAbstract> map = new HashMap<>();
        if (CollUtil.isEmpty(codes)) {
            return map;
        }
        List<StockFinAbstract> rows = stockFinAbstractMapper.selectList(
                new LambdaQueryWrapper<StockFinAbstract>()
                        .in(StockFinAbstract::getCode, codes)
                        .orderByDesc(StockFinAbstract::getReportDate));
        for (StockFinAbstract row : rows) {
            if (Objects.isNull(row) || StringUtils.isBlank(row.getCode())) {
                continue;
            }
            map.putIfAbsent(row.getCode(), row);
        }
        return map;
    }

    /**
     * 批量取各代码最新一期财务指标
     */
    private Map<String, StockFinIndicator> loadLatestIndicatorMap(Set<String> codes) {
        Map<String, StockFinIndicator> map = new HashMap<>();
        if (CollUtil.isEmpty(codes)) {
            return map;
        }
        List<StockFinIndicator> rows = stockFinIndicatorMapper.selectList(
                new LambdaQueryWrapper<StockFinIndicator>()
                        .in(StockFinIndicator::getCode, codes)
                        .orderByDesc(StockFinIndicator::getReportDate));
        for (StockFinIndicator row : rows) {
            if (Objects.isNull(row) || StringUtils.isBlank(row.getCode())) {
                continue;
            }
            map.putIfAbsent(row.getCode(), row);
        }
        return map;
    }

    private List<String> resolveUniverseCodes(String universe, int max) {
        if ("watchlist".equals(universe)) {
            List<Watchlist> list = watchlistMapper.selectList(
                    new LambdaQueryWrapper<Watchlist>().last("LIMIT " + max));
            List<String> codes = new ArrayList<>();
            for (Watchlist w : list) {
                if (Objects.nonNull(w) && StringUtils.isNotBlank(w.getCode())) {
                    codes.add(MarketCodeUtils.normalizeCode(w.getCode()));
                }
            }
            return codes;
        }
        if ("observe".equals(universe)) {
            List<ObservePool> list = observePoolMapper.selectList(
                    new LambdaQueryWrapper<ObservePool>()
                            .ne(ObservePool::getStatus, "ARCHIVED")
                            .last("LIMIT " + max));
            List<String> codes = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (ObservePool row : list) {
                if (Objects.isNull(row) || StringUtils.isBlank(row.getCode())) {
                    continue;
                }
                String c = MarketCodeUtils.normalizeCode(row.getCode());
                if (seen.add(c)) {
                    codes.add(c);
                }
            }
            return codes;
        }
        // market：有 PE/PB、非 ST、流通市值排序取样
        List<StockBasic> basics = stockBasicMapper.selectList(
                new LambdaQueryWrapper<StockBasic>()
                        .isNotNull(StockBasic::getPeTtm)
                        .gt(StockBasic::getPeTtm, 0)
                        .isNotNull(StockBasic::getCircMv)
                        .and(w -> w.isNull(StockBasic::getStFlag).or().eq(StockBasic::getStFlag, 0))
                        .orderByDesc(StockBasic::getCircMv)
                        .last("LIMIT " + max));
        List<String> codes = new ArrayList<>();
        for (StockBasic b : basics) {
            if (Objects.nonNull(b) && StringUtils.isNotBlank(b.getCode())) {
                codes.add(b.getCode());
            }
        }
        return codes;
    }

    private Map<String, StockBasic> loadBasicMap(List<String> codes) {
        Map<String, StockBasic> map = new HashMap<>();
        if (CollUtil.isEmpty(codes)) {
            return map;
        }
        List<StockBasic> list = stockBasicMapper.selectList(
                new LambdaQueryWrapper<StockBasic>().in(StockBasic::getCode, codes));
        for (StockBasic b : list) {
            map.put(b.getCode(), b);
        }
        return map;
    }

    private ValuationBriefResp toBrief(ValuationResp full) {
        if (Objects.isNull(full)) {
            return ValuationBriefResp.builder().level("UNKNOWN").levelLabel("数据不足").scoreDelta(0).build();
        }
        String level = StringUtils.isBlank(full.getLevel()) ? "" : full.getLevel();
        int delta = switch (level) {
            case "UNDERVALUED" -> 10;
            case "SLIGHTLY_CHEAP" -> 5;
            case "FAIR" -> 0;
            case "SLIGHTLY_EXPENSIVE" -> -6;
            case "OVERVALUED" -> -12;
            default -> 0;
        };
        return ValuationBriefResp.builder()
                .code(full.getCode())
                .level(full.getLevel())
                .levelLabel(full.getLevelLabel())
                .score(full.getScore())
                .peTtm(full.getPeTtm())
                .pb(full.getPb())
                .pePercentile(full.getPePercentile())
                .peg(full.getPeg())
                .marginOfSafety(full.getMarginOfSafety())
                .summary(full.getSummary())
                .scoreDelta(delta)
                .build();
    }

    private ValuationResp unknownResp(String code, String note) {
        return ValuationResp.builder()
                .code(code)
                .level("UNKNOWN")
                .levelLabel("数据不足")
                .score(new BigDecimal("50.0"))
                .summary(note)
                .actionHint("数据不足 · 先补基本面/行情后再评")
                .dimensions(List.of())
                .bullPoints(List.of())
                .bearPoints(List.of(note))
                .assumptions(List.of())
                .dataNote(note)
                .build();
    }

    private String buildDataNote(StockFinAbstract abs, StockFinIndicator ind, BigDecimal pe, BigDecimal pb, int peers) {
        List<String> parts = new ArrayList<>();
        parts.add("行情 PE/PB 来自 stock_basic");
        if (Objects.nonNull(abs) || Objects.nonNull(ind)) {
            parts.add("财务来自本地 fin 表");
        } else {
            parts.add("缺少财务摘要/指标，质量与增长维度偏弱");
        }
        if (peers > 0) {
            parts.add("同业样本 " + peers);
        }
        if (Objects.isNull(pe) && Objects.isNull(pb)) {
            parts.add("PE/PB 均缺失");
        }
        return String.join("；", parts);
    }

    private static String fmt(BigDecimal v) {
        if (Objects.isNull(v)) {
            return "-";
        }
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        if (Objects.isNull(values)) {
            return null;
        }
        for (T v : values) {
            if (Objects.nonNull(v)) {
                return v;
            }
        }
        return null;
    }

    private static LocalDate firstNonNullDate(LocalDate a, LocalDate b) {
        return Objects.nonNull(a) ? a : b;
    }

    private static final class IndustryStats {
        private int peerCount;
        private final List<BigDecimal> peList = new ArrayList<>();
        private final List<BigDecimal> pbList = new ArrayList<>();
        private BigDecimal peMedian;
        private BigDecimal pbMedian;
    }

    private static final class LevelBand {
        private final String level;
        private final String label;

        private LevelBand(String level, String label) {
            this.level = level;
            this.label = label;
        }
    }
}
