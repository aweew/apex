package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.csv.CsvData;
import cn.hutool.core.text.csv.CsvReader;
import cn.hutool.core.text.csv.CsvRow;
import cn.hutool.core.text.csv.CsvUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.executor.TransactionalAsyncExecutor;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.config.ApexProperties;
import com.awe.apex.quant.domain.dto.CorrelationMatrixResp;
import com.awe.apex.quant.domain.dto.WatchlistAddItem;
import com.awe.apex.quant.domain.dto.WatchlistAddReq;
import com.awe.apex.quant.domain.dto.WatchlistAddResp;
import com.awe.apex.quant.domain.dto.WatchlistImportReq;
import com.awe.apex.quant.domain.dto.WatchlistImportResp;
import com.awe.apex.quant.domain.dto.WatchlistMoverResp;
import com.awe.apex.quant.domain.dto.WatchlistResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.Watchlist;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.WatchlistMapper;
import com.awe.apex.quant.market.MarketCodeUtils;
import com.awe.apex.quant.service.IBarDailyService;
import com.awe.apex.quant.service.IStockService;
import com.awe.apex.quant.service.IWatchlistService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 自选股服务实现
 */
@Slf4j
@Service
public class WatchlistServiceImpl extends ServiceImpl<WatchlistMapper, Watchlist> implements IWatchlistService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Resource
    private ApexProperties apexProperties;

    @Resource
    private StockBasicMapper stockBasicMapper;

    @Resource
    private BarDailyMapper barDailyMapper;

    @Resource
    private IStockService stockService;

    @Resource
    private IBarDailyService barDailyService;

    /**
     * 自选导入后后台预热专用线程
     */
    private final Executor preheatExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "watchlist-preheat");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 查询自选列表
     *
     * @param groupName 分组，可空
     * @return 自选列表
     */
    @Override
    public List<WatchlistResp> listWatchlist(String groupName) {
        List<Watchlist> list = list(Wrappers.<Watchlist>lambdaQuery()
                .eq(StringUtils.isNotBlank(groupName), Watchlist::getGroupName, groupName)
                .orderByAsc(Watchlist::getCode));
        if (CollUtil.isEmpty(list)) {
            return new ArrayList<>();
        }

        Set<String> codes = new HashSet<>();
        for (Watchlist item : list) {
            codes.add(item.getCode());
        }

        Map<String, StockBasic> basicMap = new HashMap<>();
        List<StockBasic> basics = stockBasicMapper.selectList(Wrappers.<StockBasic>lambdaQuery()
                .in(StockBasic::getCode, codes));
        for (StockBasic basic : basics) {
            basicMap.put(basic.getCode(), basic);
        }

        Map<String, LocalDate> lastBarMap = new HashMap<>();
        Map<String, Integer> barCountMap = new HashMap<>();
        List<Map<String, Object>> stats = barDailyMapper.selectMaps(Wrappers.<BarDaily>query()
                .select("code", "MAX(trade_date) AS tradeDate", "COUNT(1) AS cnt")
                .in("code", codes)
                .groupBy("code"));
        for (Map<String, Object> row : stats) {
            String code = String.valueOf(row.get("code"));
            Object tradeDate = row.get("tradeDate");
            Object cnt = row.get("cnt");
            if (Objects.nonNull(tradeDate)) {
                lastBarMap.put(code, LocalDate.parse(String.valueOf(tradeDate).substring(0, 10)));
            }
            if (Objects.nonNull(cnt)) {
                barCountMap.put(code, Integer.parseInt(String.valueOf(cnt)));
            }
        }

        Map<String, List<BigDecimal>> recentCloses = new HashMap<>();
        List<BarDaily> recentBars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .in(BarDaily::getCode, codes)
                .ge(BarDaily::getTradeDate, LocalDate.now().minusDays(140))
                .orderByAsc(BarDaily::getTradeDate));
        for (BarDaily bar : recentBars) {
            if (Objects.isNull(bar.getClosePrice())) {
                continue;
            }
            recentCloses.computeIfAbsent(bar.getCode(), k -> new ArrayList<>()).add(bar.getClosePrice());
        }

        List<BigDecimal> hs300Closes = new ArrayList<>();
        List<BarDaily> hs300Bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, "000300")
                .ge(BarDaily::getTradeDate, LocalDate.now().minusDays(140))
                .orderByAsc(BarDaily::getTradeDate));
        for (BarDaily bar : hs300Bars) {
            if (Objects.nonNull(bar.getClosePrice())) {
                hs300Closes.add(bar.getClosePrice());
            }
        }
        BigDecimal hs300Ret20 = periodReturnPct(hs300Closes, 20);

        LocalDate staleBefore = LocalDate.now().minusDays(10);
        List<WatchlistResp> result = new ArrayList<>();
        for (Watchlist item : list) {
            StockBasic basic = basicMap.get(item.getCode());
            LocalDate lastBar = lastBarMap.get(item.getCode());
            Integer barCount = barCountMap.getOrDefault(item.getCode(), 0);
            String syncStatus = "EMPTY";
            if (barCount > 0 && Objects.nonNull(lastBar)) {
                syncStatus = lastBar.isBefore(staleBefore) ? "STALE" : "OK";
            }
            String name = item.getName();
            if (StringUtils.isBlank(name) && Objects.nonNull(basic)) {
                name = basic.getName();
            }
            List<BigDecimal> closes = recentCloses.getOrDefault(item.getCode(), List.of());
            BigDecimal ret20 = periodReturnPct(closes, 20);
            BigDecimal rs20 = null;
            if (Objects.nonNull(ret20) && Objects.nonNull(hs300Ret20)) {
                rs20 = ret20.subtract(hs300Ret20).setScale(2, RoundingMode.HALF_UP);
            }
            result.add(WatchlistResp.builder()
                    .id(item.getId())
                    .code(item.getCode())
                    .name(name)
                    .market(item.getMarket())
                    .groupName(item.getGroupName())
                    .source(item.getSource())
                    .latestPrice(Objects.nonNull(basic) ? basic.getLatestPrice() : null)
                    .pctChg(Objects.nonNull(basic) ? basic.getPctChg() : null)
                    .pctChg5(periodReturnPct(closes, 5))
                    .pctChg20(ret20)
                    .pctChg60(periodReturnPct(closes, 60))
                    .rs20VsHs300(rs20)
                    .peTtm(Objects.nonNull(basic) ? basic.getPeTtm() : null)
                    .pb(Objects.nonNull(basic) ? basic.getPb() : null)
                    .industry(Objects.nonNull(basic) ? basic.getIndustry() : null)
                    .totalMv(Objects.nonNull(basic) ? basic.getTotalMv() : null)
                    .circMv(Objects.nonNull(basic) ? basic.getCircMv() : null)
                    .lastBarDate(lastBar)
                    .barCount(barCount)
                    .syncStatus(syncStatus)
                    .build());
        }
        return result;
    }

    private BigDecimal periodReturnPct(List<BigDecimal> closes, int lookback) {
        if (closes == null || closes.size() <= lookback) {
            return null;
        }
        BigDecimal end = closes.get(closes.size() - 1);
        BigDecimal start = closes.get(closes.size() - 1 - lookback);
        if (Objects.isNull(end) || Objects.isNull(start) || start.signum() <= 0) {
            return null;
        }
        return end.subtract(start).divide(start, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 从妙想导出文件导入自选
     *
     * @param req 导入请求
     * @return 导入结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WatchlistImportResp importFromMxFile(WatchlistImportReq req) {
        String groupName = StringUtils.isNotBlank(req.getGroupName()) ? req.getGroupName() : "默认";
        File file = resolveFile(req.getFilePath());
        if (!file.exists() || !file.isFile()) {
            throw new BusinessException("自选文件不存在: " + file.getAbsolutePath());
        }

        List<Watchlist> rows;
        String lower = file.getName().toLowerCase();
        if (lower.endsWith(".csv")) {
            rows = parseCsv(file, groupName);
        } else if (lower.endsWith(".json")) {
            rows = parseJson(file, groupName);
        } else {
            throw new BusinessException("仅支持 csv/json 文件");
        }
        if (CollUtil.isEmpty(rows)) {
            throw new BusinessException("未解析到自选数据");
        }

        int importCount = 0;
        for (Watchlist row : rows) {
            Watchlist existing = getOne(Wrappers.<Watchlist>lambdaQuery()
                    .eq(Watchlist::getCode, row.getCode())
                    .eq(Watchlist::getGroupName, groupName)
                    .last("limit 1"));
            LocalDateTime now = LocalDateTime.now();
            if (Objects.isNull(existing)) {
                row.setCreateTime(now);
                row.setUpdateTime(now);
                save(row);
            } else {
                existing.setName(row.getName());
                existing.setMarket(row.getMarket());
                existing.setSource(row.getSource());
                existing.setUpdateTime(now);
                updateById(existing);
            }
            importCount++;
        }

        // 导入完成后后台预热：先补行情快照，再分批补日线，不阻塞导入接口
        final String preheatGroup = groupName;
        TransactionalAsyncExecutor.runAfterCommit(() -> {
            try {
                log.info("自选导入后开始后台预热 groupName={}", preheatGroup);
                fillQuotes(preheatGroup, 5, 40);
                barDailyService.fillWatchlist(preheatGroup, 5, 40);
                log.info("自选导入后后台预热完成 groupName={}", preheatGroup);
            } catch (Exception ex) {
                log.warn("自选导入后后台预热失败 groupName={}, err={}", preheatGroup, ex.getMessage());
            }
        }, preheatExecutor);

        return WatchlistImportResp.builder()
                .importCount(importCount)
                .sourceFile(file.getAbsolutePath())
                .groupName(groupName)
                .message("已导入并启动后台预热（行情/日线），可在自选页查看同步进度")
                .build();
    }

    /**
     * 批量加入自选（热点/决策等入口）
     *
     * @param req 请求
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WatchlistAddResp addCodes(WatchlistAddReq req) {
        if (Objects.isNull(req) || CollUtil.isEmpty(req.getItems())) {
            throw new BusinessException("请选择要加入自选的标的");
        }
        String groupName = StringUtils.isNotBlank(req.getGroupName()) ? req.getGroupName().trim() : "我的自选";
        String source = StringUtils.isNotBlank(req.getSource()) ? req.getSource().trim() : "manual";
        int added = 0;
        int updated = 0;
        LocalDateTime now = LocalDateTime.now();
        for (WatchlistAddItem item : req.getItems()) {
            if (Objects.isNull(item) || StringUtils.isBlank(item.getCode())) {
                continue;
            }
            String code = item.getCode().trim();
            String name = StringUtils.isNotBlank(item.getName()) ? item.getName().trim() : null;
            String market = MarketCodeUtils.resolveMarket(code);
            if (StringUtils.isBlank(name)) {
                StockBasic basic = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                        .eq(StockBasic::getCode, code)
                        .last("LIMIT 1"));
                if (Objects.nonNull(basic)) {
                    name = basic.getName();
                    if (StringUtils.isNotBlank(basic.getMarket())) {
                        market = basic.getMarket();
                    }
                }
            }
            Watchlist existing = getOne(Wrappers.<Watchlist>lambdaQuery()
                    .eq(Watchlist::getCode, code)
                    .eq(Watchlist::getGroupName, groupName)
                    .last("LIMIT 1"));
            if (Objects.isNull(existing)) {
                Watchlist row = Watchlist.builder()
                        .code(code)
                        .name(name)
                        .market(market)
                        .groupName(groupName)
                        .source(source)
                        .createTime(now)
                        .updateTime(now)
                        .build();
                save(row);
                added++;
            } else {
                if (StringUtils.isNotBlank(name)) {
                    existing.setName(name);
                }
                if (StringUtils.isNotBlank(market)) {
                    existing.setMarket(market);
                }
                existing.setSource(source);
                existing.setUpdateTime(now);
                updateById(existing);
                updated++;
            }
        }
        return WatchlistAddResp.builder()
                .addedCount(added)
                .updatedCount(updated)
                .groupName(groupName)
                .message("加入自选完成 · 新增 " + added + " · 更新 " + updated + " · 分组 " + groupName)
                .build();
    }

    /**
     * 刷新分组行情快照（名称/现价/估值）
     *
     * @param groupName     分组
     * @param limit         本批上限
     * @param preferMissing 优先无估值
     * @return 成功/失败数量
     */
    @Override
    public Map<String, Object> refreshQuotes(String groupName, Integer limit, Boolean preferMissing) {
        int max = Objects.isNull(limit) ? 40 : Math.max(1, Math.min(limit, 80));
        boolean prefer = !Boolean.FALSE.equals(preferMissing);
        List<Watchlist> list = list(Wrappers.<Watchlist>lambdaQuery()
                .eq(StringUtils.isNotBlank(groupName), Watchlist::getGroupName, groupName)
                .orderByAsc(Watchlist::getCode));
        if (CollUtil.isEmpty(list)) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("successCount", 0);
            empty.put("failCount", 0);
            empty.put("details", List.of("无自选"));
            return empty;
        }
        Map<String, StockBasic> basicMap = new HashMap<>();
        List<String> codes = new ArrayList<>();
        for (Watchlist item : list) {
            codes.add(item.getCode());
        }
        List<StockBasic> basics = stockBasicMapper.selectList(Wrappers.<StockBasic>lambdaQuery()
                .in(StockBasic::getCode, codes));
        for (StockBasic basic : basics) {
            basicMap.put(basic.getCode(), basic);
        }
        List<Watchlist> ordered = new ArrayList<>();
        if (prefer) {
            Set<String> picked = new HashSet<>();
            for (Watchlist item : list) {
                StockBasic basic = basicMap.get(item.getCode());
                boolean missing = Objects.isNull(basic)
                        || Objects.isNull(basic.getPeTtm())
                        || Objects.isNull(basic.getLatestPrice());
                if (missing) {
                    ordered.add(item);
                    picked.add(item.getCode());
                }
            }
            for (Watchlist item : list) {
                if (!picked.contains(item.getCode())) {
                    ordered.add(item);
                }
            }
        } else {
            ordered.addAll(list);
        }
        if (ordered.size() > max) {
            ordered = ordered.subList(0, max);
        }
        int success = 0;
        int fail = 0;
        List<String> details = new ArrayList<>();
        for (Watchlist item : ordered) {
            try {
                stockService.syncBasic(item.getCode());
                if (StringUtils.isBlank(item.getName())) {
                    StockBasic basic = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                            .eq(StockBasic::getCode, item.getCode())
                            .last("limit 1"));
                    if (Objects.nonNull(basic) && StringUtils.isNotBlank(basic.getName())) {
                        item.setName(basic.getName());
                        item.setUpdateTime(LocalDateTime.now());
                        updateById(item);
                    }
                }
                success++;
                details.add(item.getCode() + ": OK");
                Thread.sleep(100L);
            } catch (Exception ex) {
                fail++;
                details.add(item.getCode() + ": " + ex.getMessage());
                log.warn("刷新行情失败 code={}, err={}", item.getCode(), ex.getMessage());
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("successCount", success);
        result.put("failCount", fail);
        result.put("details", details);
        return result;
    }

    /**
     * 多轮补齐行情覆盖
     *
     * @param groupName 分组
     * @param rounds    轮数
     * @param limit     每轮上限
     * @return 汇总
     */
    @Override
    public Map<String, Object> fillQuotes(String groupName, Integer rounds, Integer limit) {
        int maxRounds = Objects.isNull(rounds) ? 3 : Math.max(1, Math.min(rounds, 8));
        int perRound = Objects.isNull(limit) ? 40 : Math.max(1, Math.min(limit, 80));
        int totalSuccess = 0;
        int totalFail = 0;
        int ran = 0;
        for (int i = 0; i < maxRounds; i++) {
            Map<String, Object> round = refreshQuotes(groupName, perRound, true);
            ran++;
            totalSuccess += ((Number) round.getOrDefault("successCount", 0)).intValue();
            totalFail += ((Number) round.getOrDefault("failCount", 0)).intValue();
            int ok = ((Number) round.getOrDefault("successCount", 0)).intValue();
            if (ok == 0) {
                break;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("rounds", ran);
        result.put("totalSuccess", totalSuccess);
        result.put("totalFail", totalFail);
        result.put("message", "已刷新 " + totalSuccess + " 只行情（优先补齐无估值）");
        return result;
    }

    /**
     * 自选异动
     *
     * @param groupName 分组
     * @param threshold 阈值%
     * @param limit     每侧条数
     * @return 异动
     */
    @Override
    public WatchlistMoverResp movers(String groupName, BigDecimal threshold, Integer limit) {
        BigDecimal th = Objects.nonNull(threshold) ? threshold.abs() : new BigDecimal("5");
        int sideLimit = Objects.nonNull(limit) ? Math.max(1, Math.min(limit, 50)) : 10;
        List<WatchlistResp> list = listWatchlist(groupName);
        List<WatchlistResp> gainers = new ArrayList<>();
        List<WatchlistResp> losers = new ArrayList<>();
        for (WatchlistResp row : list) {
            if (Objects.isNull(row.getPctChg())) {
                continue;
            }
            if (row.getPctChg().compareTo(th) >= 0) {
                gainers.add(row);
            } else if (row.getPctChg().compareTo(th.negate()) <= 0) {
                losers.add(row);
            }
        }
        gainers.sort(Comparator.comparing(WatchlistResp::getPctChg, Comparator.nullsLast(Comparator.reverseOrder())));
        losers.sort(Comparator.comparing(WatchlistResp::getPctChg, Comparator.nullsLast(Comparator.naturalOrder())));
        if (gainers.size() > sideLimit) {
            gainers = new ArrayList<>(gainers.subList(0, sideLimit));
        }
        if (losers.size() > sideLimit) {
            losers = new ArrayList<>(losers.subList(0, sideLimit));
        }
        return WatchlistMoverResp.builder()
                .threshold(th)
                .gainers(gainers)
                .losers(losers)
                .message("涨跌超 " + th + "%：大涨 " + gainers.size() + " / 大跌 " + losers.size())
                .build();
    }

    /**
     * 自选相关性
     *
     * @param groupName 分组
     * @param limit     标的数
     * @param lookback  回看日
     * @return 矩阵
     */
    @Override
    public CorrelationMatrixResp correlation(String groupName, Integer limit, Integer lookback) {
        int n = Objects.nonNull(limit) ? Math.max(2, Math.min(limit, 12)) : 8;
        int days = Objects.nonNull(lookback) ? Math.max(20, Math.min(lookback, 250)) : 60;
        List<WatchlistResp> list = listWatchlist(groupName);
        list.sort(Comparator.comparing(WatchlistResp::getPctChg, Comparator.nullsLast(Comparator.reverseOrder())));
        List<WatchlistResp> picked = new ArrayList<>();
        for (WatchlistResp row : list) {
            if (Objects.nonNull(row.getBarCount()) && row.getBarCount() >= days) {
                picked.add(row);
            }
            if (picked.size() >= n) {
                break;
            }
        }
        if (picked.size() < 2) {
            return CorrelationMatrixResp.builder()
                    .codes(List.of())
                    .names(List.of())
                    .matrix(List.of())
                    .sampleDays(0)
                    .message("有效标的不足，请先同步日线")
                    .build();
        }
        LocalDate end = LocalDate.now();
        LocalDate begin = end.minusDays(days * 2L);
        List<String> codes = new ArrayList<>();
        List<String> names = new ArrayList<>();
        Map<String, Map<LocalDate, BigDecimal>> closes = new LinkedHashMap<>();
        for (WatchlistResp row : picked) {
            codes.add(row.getCode());
            names.add(StringUtils.isNotBlank(row.getName()) ? row.getName() : row.getCode());
            List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                    .eq(BarDaily::getCode, row.getCode())
                    .ge(BarDaily::getTradeDate, begin)
                    .le(BarDaily::getTradeDate, end)
                    .orderByDesc(BarDaily::getTradeDate)
                    .last("limit " + (days + 5)));
            Map<LocalDate, BigDecimal> map = new HashMap<>();
            for (BarDaily bar : bars) {
                if (Objects.nonNull(bar.getClosePrice())) {
                    map.put(bar.getTradeDate(), bar.getClosePrice());
                }
            }
            closes.put(row.getCode(), map);
        }
        TreeSet<LocalDate> common = null;
        for (String code : codes) {
            TreeSet<LocalDate> dates = new TreeSet<>(closes.get(code).keySet());
            if (common == null) {
                common = dates;
            } else {
                common.retainAll(dates);
            }
        }
        if (common == null || common.size() < 10) {
            return CorrelationMatrixResp.builder()
                    .codes(codes)
                    .names(names)
                    .matrix(List.of())
                    .sampleDays(0)
                    .message("共同交易日不足")
                    .build();
        }
        List<LocalDate> dateList = new ArrayList<>(common);
        if (dateList.size() > days + 1) {
            dateList = dateList.subList(dateList.size() - (days + 1), dateList.size());
        }
        Map<String, List<Double>> returns = new HashMap<>();
        for (String code : codes) {
            List<Double> rets = new ArrayList<>();
            Map<LocalDate, BigDecimal> px = closes.get(code);
            for (int i = 1; i < dateList.size(); i++) {
                BigDecimal prev = px.get(dateList.get(i - 1));
                BigDecimal curr = px.get(dateList.get(i));
                if (Objects.nonNull(prev) && prev.signum() > 0 && Objects.nonNull(curr)) {
                    rets.add(curr.subtract(prev).divide(prev, MathContext.DECIMAL64).doubleValue());
                } else {
                    rets.add(0d);
                }
            }
            returns.put(code, rets);
        }
        List<List<BigDecimal>> matrix = new ArrayList<>();
        for (String a : codes) {
            List<BigDecimal> row = new ArrayList<>();
            for (String b : codes) {
                row.add(pearson(returns.get(a), returns.get(b)));
            }
            matrix.add(row);
        }
        return CorrelationMatrixResp.builder()
                .codes(codes)
                .names(names)
                .matrix(matrix)
                .sampleDays(dateList.size() - 1)
                .message("近 " + (dateList.size() - 1) + " 日收益相关（涨幅前列）")
                .build();
    }

    private BigDecimal pearson(List<Double> xs, List<Double> ys) {
        int n = Math.min(xs.size(), ys.size());
        if (n < 3) {
            return BigDecimal.ZERO;
        }
        double sx = 0, sy = 0;
        for (int i = 0; i < n; i++) {
            sx += xs.get(i);
            sy += ys.get(i);
        }
        double mx = sx / n;
        double my = sy / n;
        double num = 0, dx = 0, dy = 0;
        for (int i = 0; i < n; i++) {
            double a = xs.get(i) - mx;
            double b = ys.get(i) - my;
            num += a * b;
            dx += a * a;
            dy += b * b;
        }
        if (dx == 0 || dy == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(num / Math.sqrt(dx * dy)).setScale(4, RoundingMode.HALF_UP);
    }

    private File resolveFile(String filePath) {
        if (StringUtils.isBlank(filePath)) {
            throw new BusinessException("filePath 不能为空");
        }
        File direct = new File(filePath);
        if (direct.isAbsolute() || direct.exists()) {
            return direct;
        }
        String base = apexProperties.getMxOutputDir();
        if (StringUtils.isBlank(base)) {
            return direct;
        }
        return new File(base, filePath);
    }

    private List<Watchlist> parseCsv(File file, String groupName) {
        List<Watchlist> rows = new ArrayList<>();
        CsvReader reader = CsvUtil.getReader();
        CsvData data = reader.read(FileUtil.getReader(file, Charset.forName("UTF-8")));
        List<String> header = data.getRow(0);
        if (CollUtil.isEmpty(header)) {
            return rows;
        }
        int codeIdx = findHeaderIndex(header, "代码", "SECURITY_CODE");
        int nameIdx = findHeaderIndex(header, "名称", "SECURITY_SHORT_NAME");
        int marketIdx = findHeaderIndex(header, "市场代码简称", "MARKET_SHORT_NAME", "市场");
        if (codeIdx < 0) {
            throw new BusinessException("CSV 缺少代码列");
        }
        for (int i = 1; i < data.getRowCount(); i++) {
            CsvRow row = data.getRow(i);
            String code = MarketCodeUtils.normalizeCode(safeGet(row, codeIdx));
            if (StringUtils.isBlank(code)) {
                continue;
            }
            String name = nameIdx >= 0 ? safeGet(row, nameIdx) : null;
            String market = marketIdx >= 0 ? safeGet(row, marketIdx) : MarketCodeUtils.resolveMarket(code);
            rows.add(Watchlist.builder()
                    .code(code)
                    .name(name)
                    .market(normalizeMarket(market))
                    .groupName(groupName)
                    .source("mx-csv")
                    .deleted(0)
                    .build());
        }
        return rows;
    }

    private List<Watchlist> parseJson(File file, String groupName) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(file);
            JsonNode dataList = root.path("data").path("allResults").path("result").path("dataList");
            if (!dataList.isArray() || dataList.isEmpty()) {
                throw new BusinessException("JSON 中未找到 data.allResults.result.dataList");
            }
            List<Watchlist> rows = new ArrayList<>();
            for (JsonNode node : dataList) {
                String code = MarketCodeUtils.normalizeCode(text(node, "SECURITY_CODE"));
                if (StringUtils.isBlank(code)) {
                    continue;
                }
                String name = text(node, "SECURITY_SHORT_NAME");
                String market = text(node, "MARKET_SHORT_NAME");
                rows.add(Watchlist.builder()
                        .code(code)
                        .name(name)
                        .market(normalizeMarket(StringUtils.isNotBlank(market) ? market : MarketCodeUtils.resolveMarket(code)))
                        .groupName(groupName)
                        .source("mx-json")
                        .deleted(0)
                        .build());
            }
            return rows;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("解析 JSON 失败: " + ex.getMessage(), ex);
        }
    }

    private int findHeaderIndex(List<String> header, String... candidates) {
        for (int i = 0; i < header.size(); i++) {
            String raw = header.get(i);
            if (raw == null) {
                continue;
            }
            String h = raw.replace("\uFEFF", "").trim();
            for (String candidate : candidates) {
                if (h.equalsIgnoreCase(candidate) || h.contains(candidate)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String safeGet(CsvRow row, int idx) {
        if (idx < 0 || idx >= row.size()) {
            return null;
        }
        return row.get(idx);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return Objects.isNull(value) || value.isNull() ? null : value.asText();
    }

    private String normalizeMarket(String market) {
        if (StringUtils.isBlank(market)) {
            return null;
        }
        String value = market.trim().toUpperCase();
        if (value.startsWith("SH") || value.contains("沪")) {
            return "SH";
        }
        if (value.startsWith("SZ") || value.contains("深")) {
            return "SZ";
        }
        if (value.startsWith("BJ") || value.contains("北")) {
            return "BJ";
        }
        return value;
    }
}
