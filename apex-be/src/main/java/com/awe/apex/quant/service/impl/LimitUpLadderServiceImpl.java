package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.config.ScriptDatabaseEnvironment;
import com.awe.apex.quant.domain.dto.LimitUpEffectResp;
import com.awe.apex.quant.domain.dto.LimitUpLadderResp;
import com.awe.apex.quant.domain.dto.LimitUpRefreshResp;
import com.awe.apex.quant.domain.dto.LimitUpStockItem;
import com.awe.apex.quant.domain.dto.LimitUpThemeStat;
import com.awe.apex.quant.domain.dto.LimitUpTier;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.LimitUpPool;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.LimitUpPoolMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.market.MarketCodeUtils;
import com.awe.apex.quant.service.ILimitUpLadderService;
import com.awe.apex.quant.util.ProcessIoUtils;
import com.awe.apex.quant.util.PythonCommandResolver;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 连板天梯服务
 */
@Slf4j
@Service
public class LimitUpLadderServiceImpl implements ILimitUpLadderService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String[] PROMOTE_CN = {
            "", "首板", "一进二", "二进三", "三进四", "四进五", "五进六",
            "六进七", "七进八", "八进九", "九进十"
    };

    @Resource
    private LimitUpPoolMapper limitUpPoolMapper;

    @Resource
    private StockBasicMapper stockBasicMapper;

    @Resource
    private BarDailyMapper barDailyMapper;

    @Resource
    private ScriptDatabaseEnvironment scriptDatabaseEnvironment;

    @Value("${apex.hot.python-cmd:python}")
    private String pythonCmd;

    @Value("${apex.limit-up.script-path:}")
    private String scriptPathConfig;

    /**
     * 查询连板天梯
     *
     * @param tradeDate 交易日可空
     * @return 天梯
     */
    @Override
    public LimitUpLadderResp ladder(String tradeDate) {
        List<LocalDate> available = listAvailableDates();
        LocalDate resolved = resolveTradeDate(tradeDate, available);
        if (Objects.isNull(resolved)) {
            return LimitUpLadderResp.builder()
                    .availableDates(available)
                    .totalCount(0)
                    .maxLianban(0)
                    .themes(List.of())
                    .tiers(List.of())
                    .message("暂无涨停池数据，请先刷新")
                    .build();
        }
        List<LimitUpPool> today = limitUpPoolMapper.selectList(Wrappers.<LimitUpPool>lambdaQuery()
                .eq(LimitUpPool::getTradeDate, resolved)
                .orderByDesc(LimitUpPool::getLianban)
                .orderByAsc(LimitUpPool::getFirstSealTime)
                .orderByAsc(LimitUpPool::getCode));
        LocalDate prevDate = findPrevDate(resolved, available);
        Map<String, Integer> prevLianban = loadPrevLianban(prevDate);

        Map<Integer, List<LimitUpStockItem>> byTier = new LinkedHashMap<>();
        Set<String> todayCodes = new HashSet<>();
        int maxLb = 0;
        LocalDateTime syncedAt = null;
        for (LimitUpPool row : today) {
            int lb = Objects.nonNull(row.getLianban()) ? row.getLianban() : 1;
            maxLb = Math.max(maxLb, lb);
            todayCodes.add(row.getCode());
            byTier.computeIfAbsent(lb, k -> new ArrayList<>()).add(toStockItem(row, false));
            if (Objects.isNull(syncedAt) && Objects.nonNull(row.getSyncedAt())) {
                syncedAt = row.getSyncedAt();
            }
        }

        // 昨日连板（≥2板）今日未涨停：挂到「晋级目标」高度（昨N板 → 今日N+1板梯队），排除昨首板
        int failCount = appendFailedStocks(byTier, prevDate, todayCodes, resolved);

        List<Integer> tiersDesc = byTier.keySet().stream()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
        List<LimitUpTier> tiers = new ArrayList<>();
        for (Integer lb : tiersDesc) {
            List<LimitUpStockItem> items = byTier.get(lb);
            BigDecimal rate = null;
            String promoteLabel = null;
            if (lb >= 2) {
                promoteLabel = promoteLabel(lb);
                rate = calcPromoteRate(lb, prevLianban, today);
            }
            tiers.add(LimitUpTier.builder()
                    .lianban(lb)
                    .title(lb <= 1 ? "首板" : lb + "板")
                    .promoteLabel(promoteLabel)
                    .promoteRate(rate)
                    .count(items.size())
                    .stocks(items)
                    .build());
        }

        List<LimitUpThemeStat> themes = buildThemes(today);
        LimitUpEffectResp effect = buildEffect(prevDate, prevLianban, today);
        String msg = resolved + " 涨停 " + today.size() + " 家 · 最高 " + maxLb + " 板";
        if (failCount > 0) {
            msg = msg + " · 断板 " + failCount + " 家";
        }
        if (Objects.nonNull(effect) && Objects.nonNull(effect.getPromoteRate())) {
            msg = msg + " · 晋级率 " + effect.getPromoteRate() + "%";
        }
        return LimitUpLadderResp.builder()
                .tradeDate(resolved)
                .availableDates(available)
                .totalCount(today.size())
                .maxLianban(maxLb)
                .themes(themes)
                .tiers(tiers)
                .syncedAt(syncedAt)
                .effect(effect)
                .message(msg)
                .build();
    }

    /**
     * 刷新涨停池并返回天梯
     *
     * @param tradeDate 交易日可空
     * @return 结果
     */
    @Override
    public LimitUpRefreshResp refresh(String tradeDate) {
        Path script = resolveScript();
        if (Objects.isNull(script) || !Files.isRegularFile(script)) {
            throw new BusinessException("未找到涨停同步脚本 sync_limit_up.py");
        }
        LocalDate day = parseDateOrNull(tradeDate);
        if (Objects.isNull(day)) {
            day = LocalDate.now();
        }
        List<String> command = new ArrayList<>();
        command.add(PythonCommandResolver.resolve(pythonCmd));
        command.add("-u");
        command.add(script.toAbsolutePath().toString());
        command.add("--date");
        command.add(day.format(DAY));
        command.add("--with-prev");
        String logText = runScript(command, 180);
        LimitUpLadderResp ladder = ladder(day.toString());
        return LimitUpRefreshResp.builder()
                .message("涨停池已刷新")
                .log(logText)
                .ladder(ladder)
                .build();
    }

    private List<LimitUpThemeStat> buildThemes(List<LimitUpPool> today) {
        Map<String, LimitUpThemeStat> map = new HashMap<>();
        for (LimitUpPool row : today) {
            String theme = StringUtils.isNotBlank(row.getTheme()) ? row.getTheme().trim()
                    : (StringUtils.isNotBlank(row.getIndustry()) ? row.getIndustry().trim() : null);
            if (StringUtils.isBlank(theme)) {
                continue;
            }
            LimitUpThemeStat stat = map.computeIfAbsent(theme, t -> LimitUpThemeStat.builder()
                    .theme(t)
                    .count(0)
                    .maxLianban(0)
                    .build());
            stat.setCount(stat.getCount() + 1);
            int lb = Objects.nonNull(row.getLianban()) ? row.getLianban() : 1;
            if (lb > stat.getMaxLianban()) {
                stat.setMaxLianban(lb);
            }
        }
        return map.values().stream()
                .sorted(Comparator.comparing(LimitUpThemeStat::getCount).reversed()
                        .thenComparing(LimitUpThemeStat::getMaxLianban, Comparator.reverseOrder()))
                .limit(12)
                .collect(Collectors.toList());
    }

    /**
     * 计算 N 板晋级率：今日 N 板家数 / 昨日 (N-1) 板家数。
     * 采用市场常用口径，不依赖代码精确匹配，避免因代码格式差异导致全为 0%。
     */
    private BigDecimal calcPromoteRate(int targetLianban, Map<String, Integer> prevLianban,
                                       List<LimitUpPool> today) {
        if (prevLianban.isEmpty()) {
            return null;
        }
        int base = 0;
        for (Integer lb : prevLianban.values()) {
            if (Objects.nonNull(lb) && lb == targetLianban - 1) {
                base++;
            }
        }
        if (base <= 0) {
            return null;
        }
        int success = 0;
        for (LimitUpPool row : today) {
            if (Objects.nonNull(row.getLianban()) && row.getLianban() == targetLianban) {
                success++;
            }
        }
        return BigDecimal.valueOf(success * 100.0 / base).setScale(1, RoundingMode.HALF_UP);
    }

    private Map<String, Integer> loadPrevLianban(LocalDate prevDate) {
        Map<String, Integer> map = new HashMap<>();
        if (Objects.isNull(prevDate)) {
            return map;
        }
        List<LimitUpPool> prev = limitUpPoolMapper.selectList(Wrappers.<LimitUpPool>lambdaQuery()
                .eq(LimitUpPool::getTradeDate, prevDate)
                .select(LimitUpPool::getCode, LimitUpPool::getLianban));
        for (LimitUpPool row : prev) {
            if (StringUtils.isNotBlank(row.getCode())) {
                map.put(row.getCode(), row.getLianban());
            }
        }
        return map;
    }

    /**
     * 昨日 ≥2 板且今日未涨停的个股，挂到晋级目标高度（昨 N 板 → N+1 板梯队）并标记 failed。
     * 例：昨 8 板今日断板 → 出现在「9板 / 八进九」梯队。
     * 涨跌幅优先 stock_basic → 当日日线 → 新浪实时批量补齐（stock_basic 常未全量同步）。
     *
     * @param byTier     梯队
     * @param prevDate   上一交易日
     * @param todayCodes 今日涨停代码
     * @param tradeDate  当前复盘日（用于取当日涨跌幅）
     * @return 断板家数
     */
    private int appendFailedStocks(Map<Integer, List<LimitUpStockItem>> byTier,
                                   LocalDate prevDate, Set<String> todayCodes, LocalDate tradeDate) {
        if (Objects.isNull(prevDate)) {
            return 0;
        }
        List<LimitUpPool> prevRows = limitUpPoolMapper.selectList(Wrappers.<LimitUpPool>lambdaQuery()
                .eq(LimitUpPool::getTradeDate, prevDate)
                .ge(LimitUpPool::getLianban, 2)
                .orderByDesc(LimitUpPool::getLianban)
                .orderByAsc(LimitUpPool::getCode));
        if (CollUtil.isEmpty(prevRows)) {
            return 0;
        }
        List<String> failCodes = new ArrayList<>();
        List<LimitUpPool> failRows = new ArrayList<>();
        for (LimitUpPool row : prevRows) {
            if (StringUtils.isBlank(row.getCode()) || todayCodes.contains(row.getCode())) {
                continue;
            }
            failCodes.add(row.getCode());
            failRows.add(row);
        }
        if (CollUtil.isEmpty(failRows)) {
            return 0;
        }
        Map<String, StockBasic> basics = new HashMap<>();
        List<StockBasic> basicList = stockBasicMapper.selectList(Wrappers.<StockBasic>lambdaQuery()
                .in(StockBasic::getCode, failCodes));
        for (StockBasic basic : basicList) {
            basics.put(basic.getCode(), basic);
        }
        Map<String, BarDaily> dayBars = loadDayBars(failCodes, tradeDate);
        List<String> needLive = new ArrayList<>();
        for (String code : failCodes) {
            StockBasic basic = basics.get(code);
            if (Objects.nonNull(basic) && Objects.nonNull(basic.getPctChg())) {
                continue;
            }
            BarDaily bar = dayBars.get(code);
            if (Objects.nonNull(bar) && Objects.nonNull(bar.getPctChg())) {
                continue;
            }
            needLive.add(code);
        }
        Map<String, LiveSnap> liveSnaps = fetchLiveSnaps(needLive);
        for (LimitUpPool row : failRows) {
            int prevLb = Objects.nonNull(row.getLianban()) ? row.getLianban() : 2;
            // 晋级目标高度：昨 N 板失败 → 挂在 N+1 板（与「N进N+1」标签对齐）
            int targetLb = prevLb + 1;
            LimitUpStockItem item = toStockItem(row, true);
            // 卡片连板数保留昨高度，便于识别「几进几失败」；先清空昨涨停涨跌幅，再填今日涨跌
            item.setLianban(prevLb);
            item.setPctChg(null);
            item.setLatestPrice(null);
            StockBasic basic = basics.get(row.getCode());
            if (Objects.nonNull(basic)) {
                if (StringUtils.isBlank(item.getName()) && StringUtils.isNotBlank(basic.getName())) {
                    item.setName(basic.getName());
                }
                item.setPctChg(basic.getPctChg());
                item.setLatestPrice(basic.getLatestPrice());
                if (StringUtils.isBlank(item.getTheme()) && StringUtils.isNotBlank(basic.getIndustry())) {
                    item.setTheme(basic.getIndustry());
                }
            }
            if (Objects.isNull(item.getPctChg()) || Objects.isNull(item.getLatestPrice())) {
                BarDaily bar = dayBars.get(row.getCode());
                if (Objects.nonNull(bar)) {
                    if (Objects.isNull(item.getPctChg()) && Objects.nonNull(bar.getPctChg())) {
                        item.setPctChg(bar.getPctChg());
                    }
                    if (Objects.isNull(item.getLatestPrice()) && Objects.nonNull(bar.getClosePrice())) {
                        item.setLatestPrice(bar.getClosePrice());
                    }
                }
            }
            if (Objects.isNull(item.getPctChg()) || Objects.isNull(item.getLatestPrice())) {
                LiveSnap snap = liveSnaps.get(row.getCode());
                if (Objects.nonNull(snap)) {
                    if (Objects.isNull(item.getPctChg()) && Objects.nonNull(snap.pctChg)) {
                        item.setPctChg(snap.pctChg);
                    }
                    if (Objects.isNull(item.getLatestPrice()) && Objects.nonNull(snap.price)) {
                        item.setLatestPrice(snap.price);
                    }
                    if (StringUtils.isBlank(item.getName()) && StringUtils.isNotBlank(snap.name)) {
                        item.setName(snap.name);
                    }
                }
            }
            // 断板不再展示昨封板时间/封单，避免误解为今日封板
            item.setFirstSealTime(null);
            item.setLastSealTime(null);
            item.setSealAmount(null);
            item.setBreakCount(null);
            item.setYizi(false);
            byTier.computeIfAbsent(targetLb, k -> new ArrayList<>()).add(item);
        }
        return failRows.size();
    }

    private Map<String, BarDaily> loadDayBars(List<String> codes, LocalDate tradeDate) {
        Map<String, BarDaily> map = new HashMap<>();
        if (CollUtil.isEmpty(codes) || Objects.isNull(tradeDate)) {
            return map;
        }
        List<BarDaily> rows = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getTradeDate, tradeDate)
                .in(BarDaily::getCode, codes)
                .select(BarDaily::getCode, BarDaily::getPctChg, BarDaily::getClosePrice));
        for (BarDaily row : rows) {
            if (StringUtils.isNotBlank(row.getCode())) {
                map.put(row.getCode(), row);
            }
        }
        return map;
    }

    /**
     * 新浪批量快照补涨跌幅（断板股常缺 stock_basic 行情）
     */
    private Map<String, LiveSnap> fetchLiveSnaps(List<String> codes) {
        Map<String, LiveSnap> result = new HashMap<>();
        if (CollUtil.isEmpty(codes)) {
            return result;
        }
        int chunkSize = 40;
        for (int from = 0; from < codes.size(); from += chunkSize) {
            int to = Math.min(from + chunkSize, codes.size());
            List<String> chunk = codes.subList(from, to);
            List<String> symbols = new ArrayList<>();
            Map<String, String> symbolToCode = new HashMap<>();
            for (String code : chunk) {
                String pure = MarketCodeUtils.normalizeHoldingCode(code);
                if (StringUtils.isBlank(pure)) {
                    continue;
                }
                String market = MarketCodeUtils.resolveMarket(pure);
                String symbol;
                if ("SH".equals(market)) {
                    symbol = "sh" + pure;
                } else if ("BJ".equals(market)) {
                    symbol = "bj" + pure;
                } else {
                    symbol = "sz" + pure;
                }
                symbols.add(symbol);
                symbolToCode.put(symbol, pure);
            }
            if (symbols.isEmpty()) {
                continue;
            }
            String url = "https://hq.sinajs.cn/list=" + String.join(",", symbols);
            try (HttpResponse response = HttpRequest.get(url)
                    .timeout(8000)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://finance.sina.com.cn")
                    .execute()) {
                if (!response.isOk()) {
                    continue;
                }
                String body = response.body();
                if (response.bodyBytes() != null) {
                    body = new String(response.bodyBytes(), Charset.forName("GBK"));
                }
                String[] lines = body.split(";");
                for (String line : lines) {
                    if (StringUtils.isBlank(line) || !line.contains("=")) {
                        continue;
                    }
                    int eq = line.indexOf('=');
                    String left = line.substring(0, eq).trim();
                    int hq = left.lastIndexOf('_');
                    String symbol = hq >= 0 ? left.substring(hq + 1) : left;
                    symbol = symbol.toLowerCase();
                    String code = symbolToCode.get(symbol);
                    if (StringUtils.isBlank(code)) {
                        continue;
                    }
                    int q1 = line.indexOf('"');
                    int q2 = line.lastIndexOf('"');
                    if (q1 < 0 || q2 <= q1) {
                        continue;
                    }
                    String[] parts = line.substring(q1 + 1, q2).split(",");
                    if (parts.length < 4 || StringUtils.isBlank(parts[0])) {
                        continue;
                    }
                    BigDecimal price = parseDecimal(parts[3]);
                    BigDecimal prevClose = parseDecimal(parts[2]);
                    if (Objects.nonNull(price) && price.signum() <= 0) {
                        price = null;
                    }
                    BigDecimal pct = null;
                    if (Objects.nonNull(price) && Objects.nonNull(prevClose) && prevClose.signum() > 0) {
                        pct = price.subtract(prevClose)
                                .multiply(BigDecimal.valueOf(100))
                                .divide(prevClose, 2, RoundingMode.HALF_UP);
                    }
                    LiveSnap snap = new LiveSnap();
                    snap.name = parts[0];
                    snap.price = price;
                    snap.pctChg = pct;
                    result.put(code, snap);
                }
            } catch (Exception ex) {
                log.debug("断板股实时行情补齐失败: {}", ex.getMessage());
            }
        }
        return result;
    }

    private BigDecimal parseDecimal(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static final class LiveSnap {
        private String name;
        private BigDecimal price;
        private BigDecimal pctChg;
    }

    private LimitUpStockItem toStockItem(LimitUpPool row, boolean failed) {
        String theme = StringUtils.isNotBlank(row.getTheme()) ? row.getTheme() : row.getIndustry();
        return LimitUpStockItem.builder()
                .code(row.getCode())
                .name(row.getName())
                .lianban(row.getLianban())
                .pctChg(row.getPctChg())
                .latestPrice(row.getLatestPrice())
                .firstSealTime(formatSealTime(row.getFirstSealTime()))
                .lastSealTime(formatSealTime(row.getLastSealTime()))
                .breakCount(row.getBreakCount())
                .sealAmount(row.getSealAmount())
                .turnoverRate(row.getTurnoverRate())
                .theme(theme)
                .ztStats(row.getZtStats())
                .yizi(isYizi(row))
                .failed(failed)
                .build();
    }

    /**
     * 一字板：集合竞价封板（09:25）且当日未开板
     */
    private boolean isYizi(LimitUpPool row) {
        if (Objects.isNull(row)) {
            return false;
        }
        int breakCount = Objects.nonNull(row.getBreakCount()) ? row.getBreakCount() : 0;
        if (breakCount > 0) {
            return false;
        }
        String raw = row.getFirstSealTime();
        if (StringUtils.isBlank(raw)) {
            return false;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return false;
        }
        if (digits.length() >= 6) {
            digits = digits.substring(0, 6);
        } else {
            digits = digits.substring(0, 4);
        }
        return digits.startsWith("0925");
    }

    private LimitUpEffectResp buildEffect(LocalDate prevDate, Map<String, Integer> prevLianban,
                                          List<LimitUpPool> today) {
        if (Objects.isNull(prevDate) || prevLianban.isEmpty()) {
            return LimitUpEffectResp.builder()
                    .prevCount(0)
                    .message("缺少前一日涨停池，无法统计赚钱效应")
                    .build();
        }
        Map<String, LimitUpPool> todayMap = new HashMap<>();
        for (LimitUpPool row : today) {
            todayMap.put(row.getCode(), row);
        }
        int ok = 0;
        int hold = 0;
        int fail = 0;
        List<String> failNames = new ArrayList<>();
        List<String> prevCodes = new ArrayList<>(prevLianban.keySet());
        BigDecimal pctSum = BigDecimal.ZERO;
        int pctN = 0;
        Map<String, StockBasic> basics = new HashMap<>();
        if (CollUtil.isNotEmpty(prevCodes)) {
            List<StockBasic> list = stockBasicMapper.selectList(Wrappers.<StockBasic>lambdaQuery()
                    .in(StockBasic::getCode, prevCodes));
            for (StockBasic b : list) {
                basics.put(b.getCode(), b);
            }
        }
        for (Map.Entry<String, Integer> e : prevLianban.entrySet()) {
            String code = e.getKey();
            int prevLb = Objects.nonNull(e.getValue()) ? e.getValue() : 1;
            LimitUpPool cur = todayMap.get(code);
            if (Objects.nonNull(cur) && Objects.nonNull(cur.getLianban()) && cur.getLianban() > prevLb) {
                ok++;
            } else if (Objects.nonNull(cur) && Objects.nonNull(cur.getLianban()) && cur.getLianban() >= prevLb) {
                hold++;
            } else {
                fail++;
                StockBasic basic = basics.get(code);
                String name = Objects.nonNull(cur) ? cur.getName()
                        : (Objects.nonNull(basic) ? basic.getName() : code);
                if (failNames.size() < 12) {
                    failNames.add(name);
                }
            }
            StockBasic basic = basics.get(code);
            if (Objects.nonNull(basic) && Objects.nonNull(basic.getPctChg())) {
                pctSum = pctSum.add(basic.getPctChg());
                pctN++;
            } else if (Objects.nonNull(cur) && Objects.nonNull(cur.getPctChg())) {
                pctSum = pctSum.add(cur.getPctChg());
                pctN++;
            }
        }
        int prevCount = prevLianban.size();
        BigDecimal rate = prevCount > 0
                ? BigDecimal.valueOf(ok * 100.0 / prevCount).setScale(1, RoundingMode.HALF_UP)
                : null;
        BigDecimal avg = pctN > 0
                ? pctSum.divide(BigDecimal.valueOf(pctN), 2, RoundingMode.HALF_UP)
                : null;
        return LimitUpEffectResp.builder()
                .prevCount(prevCount)
                .promoteOk(ok)
                .promoteHold(hold)
                .promoteFail(fail)
                .promoteRate(rate)
                .avgNextPct(avg)
                .failNames(failNames)
                .message("昨涨停 " + prevCount + " · 晋级 " + ok + " · 同板 " + hold + " · 断板 " + fail
                        + (Objects.nonNull(avg) ? (" · 今日均涨跌 " + avg + "%") : ""))
                .build();
    }

    private String formatSealTime(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return raw;
        }
        digits = digits.length() >= 6 ? digits.substring(0, 6) : digits;
        if (digits.length() == 4) {
            return digits.substring(0, 2) + ":" + digits.substring(2, 4);
        }
        return digits.substring(0, 2) + ":" + digits.substring(2, 4);
    }

    private String promoteLabel(int lianban) {
        if (lianban >= 2 && lianban < PROMOTE_CN.length) {
            return PROMOTE_CN[lianban];
        }
        if (lianban >= 2) {
            return (lianban - 1) + "进" + lianban;
        }
        return null;
    }

    private List<LocalDate> listAvailableDates() {
        List<LimitUpPool> rows = limitUpPoolMapper.selectList(Wrappers.<LimitUpPool>lambdaQuery()
                .select(LimitUpPool::getTradeDate)
                .orderByDesc(LimitUpPool::getTradeDate)
                .last("LIMIT 500"));
        List<LocalDate> dates = new ArrayList<>();
        Set<LocalDate> seen = new HashSet<>();
        for (LimitUpPool row : rows) {
            if (Objects.nonNull(row.getTradeDate()) && seen.add(row.getTradeDate())) {
                dates.add(row.getTradeDate());
            }
            if (dates.size() >= 60) {
                break;
            }
        }
        return dates;
    }

    private LocalDate resolveTradeDate(String tradeDate, List<LocalDate> available) {
        LocalDate parsed = parseDateOrNull(tradeDate);
        if (Objects.nonNull(parsed) && CollUtil.isNotEmpty(available)) {
            for (LocalDate availableDate : available) {
                if (!availableDate.isAfter(parsed)) {
                    return availableDate;
                }
            }
        }
        if (CollUtil.isNotEmpty(available)) {
            return available.get(0);
        }
        return null;
    }

    private LocalDate findPrevDate(LocalDate current, List<LocalDate> available) {
        if (Objects.isNull(current) || CollUtil.isEmpty(available)) {
            return null;
        }
        for (LocalDate d : available) {
            if (d.isBefore(current)) {
                return d;
            }
        }
        return null;
    }

    private LocalDate parseDateOrNull(String tradeDate) {
        if (StringUtils.isBlank(tradeDate)) {
            return null;
        }
        String text = tradeDate.trim();
        try {
            if (text.contains("-")) {
                return LocalDate.parse(text);
            }
            return LocalDate.parse(text, DAY);
        } catch (DateTimeParseException ex) {
            throw new BusinessException("交易日格式错误: " + tradeDate);
        }
    }

    private Path resolveScript() {
        if (StringUtils.isNotBlank(scriptPathConfig)) {
            return Paths.get(scriptPathConfig.trim());
        }
        Path cwd = Paths.get("").toAbsolutePath();
        List<Path> candidates = List.of(
                cwd.resolve("scripts/market_data/sync_limit_up.py"),
                cwd.resolve("../scripts/market_data/sync_limit_up.py"),
                cwd.resolve("../../scripts/market_data/sync_limit_up.py"),
                Paths.get(System.getProperty("user.dir", "."))
                        .toAbsolutePath().normalize()
                        .resolve("scripts/market_data/sync_limit_up.py")
        );
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                return p.normalize();
            }
        }
        return null;
    }

    private String runScript(List<String> command, long timeoutSec) {
        log.info("执行涨停同步: {}", String.join(" ", command));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        scriptDatabaseEnvironment.apply(pb.environment());
        Path script = Paths.get(command.get(2));
        if (Objects.nonNull(script.getParent())) {
            pb.directory(script.getParent().toFile());
        }
        try {
            Process process = pb.start();
            Charset charset = Charset.forName("GBK");
            String out = ProcessIoUtils.readAndDrain(process.getInputStream(), charset, 20000);
            boolean finished = ProcessIoUtils.waitOrKill(process, timeoutSec);
            if (!finished) {
                throw new BusinessException("涨停同步超时");
            }
            if (process.exitValue() != 0) {
                throw new BusinessException("涨停同步失败: " + out.trim());
            }
            return StringUtils.isNotBlank(out) ? out.trim() : "ok";
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("涨停同步异常: " + ex.getMessage(), ex);
        }
    }
}
