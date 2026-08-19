package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.quant.market.TradingCalendar;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.BarSyncReq;
import com.awe.apex.quant.domain.dto.BarSyncResp;
import com.awe.apex.quant.domain.dto.FillBarsResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.DataSyncLog;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.Watchlist;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.DataSyncLogMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.WatchlistMapper;
import com.awe.apex.quant.market.DailyBarClient;
import com.awe.apex.quant.market.MarketCodeUtils;
import com.awe.apex.quant.service.IBarDailyService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 日线服务实现
 */
@Slf4j
@Service
public class BarDailyServiceImpl extends ServiceImpl<BarDailyMapper, BarDaily> implements IBarDailyService {

    /** 降低并发，减轻行情源限流 */
    private static final int MAX_PARALLEL = 2;
    private static final int MAX_SYNC_CODES = 80;

    @Value("${apex.bar-sync.timeout-seconds:180}")
    private int syncTimeoutSeconds;

    @Resource
    private DailyBarClient dailyBarClient;

    @Resource
    private DataSyncLogMapper dataSyncLogMapper;

    @Resource
    private StockBasicMapper stockBasicMapper;

    @Resource
    private WatchlistMapper watchlistMapper;

    @Resource
    private ApexUserContext userContext;

    /**
     * 同步日线并落库
     *
     * @param req 同步请求
     * @return 同步结果
     */
    @Override
    public BarSyncResp syncBars(BarSyncReq req) {
        if (Objects.isNull(req) || req.getCodes() == null || req.getCodes().isEmpty()) {
            throw new BusinessException("codes 不能为空");
        }
        Set<String> codes = new LinkedHashSet<>();
        for (String rawCode : req.getCodes()) {
            String code = MarketCodeUtils.normalizeCode(rawCode);
            if (StringUtils.isNotBlank(code)) {
                codes.add(code);
            }
        }
        if (codes.isEmpty()) {
            throw new BusinessException("无有效代码");
        }
        if (codes.size() > MAX_SYNC_CODES) {
            throw new BusinessException("单次最多同步 " + MAX_SYNC_CODES + " 个证券代码，全 A 日线请使用同步中心任务");
        }
        return doSync(new ArrayList<>(codes), req.getBeginDate(), req.getEndDate(), "codes=" + codes);
    }

    /**
     * 按自选分组同步日线
     *
     * @param groupName 分组
     * @param beginDate 开始
     * @param endDate   结束
     * @return 同步结果
     */
    @Override
    public BarSyncResp syncWatchlistGroup(String groupName, String beginDate, String endDate) {
        List<Watchlist> list = watchlistMapper.selectList(Wrappers.<Watchlist>lambdaQuery()
                .eq(Watchlist::getUserId, userContext.currentUserId())
                .eq(StringUtils.isNotBlank(groupName), Watchlist::getGroupName, groupName)
                .orderByAsc(Watchlist::getCode));
        if (list.isEmpty()) {
            throw new BusinessException("分组无自选: " + groupName);
        }
        Set<String> codes = new LinkedHashSet<>();
        for (Watchlist item : list) {
            codes.add(item.getCode());
            if (codes.size() >= MAX_SYNC_CODES) {
                break;
            }
        }
        String scope = "group=" + groupName + ", codes=" + codes.size()
                + (list.size() > MAX_SYNC_CODES ? ("(cap " + MAX_SYNC_CODES + ")") : "");
        return doSync(new ArrayList<>(codes), beginDate, endDate, scope);
    }

    /**
     * 仅同步缺失或过期日线的自选
     *
     * @param groupName 分组
     * @param limit     上限
     * @return 同步结果
     */
    @Override
    public BarSyncResp syncStaleWatchlist(String groupName, Integer limit) {
        int max = Objects.isNull(limit) ? 40 : Math.max(1, Math.min(limit, MAX_SYNC_CODES));
        List<Watchlist> list = watchlistMapper.selectList(Wrappers.<Watchlist>lambdaQuery()
                .eq(Watchlist::getUserId, userContext.currentUserId())
                .eq(StringUtils.isNotBlank(groupName), Watchlist::getGroupName, groupName)
                .orderByAsc(Watchlist::getCode));
        if (list.isEmpty()) {
            throw new BusinessException("分组无自选: " + groupName);
        }
        Set<String> allCodes = new LinkedHashSet<>();
        for (Watchlist item : list) {
            String code = MarketCodeUtils.normalizeCode(item.getCode());
            if (StringUtils.isNotBlank(code)) {
                allCodes.add(code);
            }
        }
        List<String> staleCodes = findStaleCodes(new ArrayList<>(allCodes), max);
        if (staleCodes.isEmpty()) {
            return emptySyncResponse();
        }
        return doSync(staleCodes, null, null, "stale group=" + groupName + ", n=" + staleCodes.size());
    }

    /**
     * 同步指定代码中缺失或过期的日线，代码会全局去重并分批处理。
     *
     * @param codes 证券代码
     * @return 同步结果
     */
    @Override
    public BarSyncResp syncStaleCodes(List<String> codes) {
        Set<String> uniqueCodes = new LinkedHashSet<>();
        if (CollUtil.isNotEmpty(codes)) {
            for (String rawCode : codes) {
                String code = MarketCodeUtils.normalizeCode(rawCode);
                if (StringUtils.isNotBlank(code)) {
                    uniqueCodes.add(code);
                }
            }
        }
        List<String> staleCodes = findStaleCodes(new ArrayList<>(uniqueCodes), Integer.MAX_VALUE);
        if (staleCodes.isEmpty()) {
            return emptySyncResponse();
        }

        int successCount = 0;
        int failCount = 0;
        int barCount = 0;
        String source = "none";
        List<String> details = new ArrayList<>();
        LocalDateTime fetchedAt = LocalDateTime.now();
        for (int start = 0; start < staleCodes.size(); start += MAX_SYNC_CODES) {
            int end = Math.min(start + MAX_SYNC_CODES, staleCodes.size());
            List<String> batchCodes = new ArrayList<>(staleCodes.subList(start, end));
            BarSyncResp batch = doSync(batchCodes, null, null,
                    "shared stale codes batch=" + (start / MAX_SYNC_CODES + 1) + ", n=" + batchCodes.size());
            successCount += Objects.nonNull(batch.getSuccessCount()) ? batch.getSuccessCount() : 0;
            failCount += Objects.nonNull(batch.getFailCount()) ? batch.getFailCount() : 0;
            barCount += Objects.nonNull(batch.getBarCount()) ? batch.getBarCount() : 0;
            if (StringUtils.isNotBlank(batch.getSource()) && !"none".equals(batch.getSource())) {
                source = batch.getSource();
            }
            if (CollUtil.isNotEmpty(batch.getDetails())) {
                details.addAll(batch.getDetails());
            }
        }
        return BarSyncResp.builder()
                .source(source)
                .fetchedAt(fetchedAt)
                .successCount(successCount)
                .failCount(failCount)
                .barCount(barCount)
                .details(details)
                .build();
    }

    private List<String> findStaleCodes(List<String> codes, int limit) {
        if (CollUtil.isEmpty(codes)) {
            return new ArrayList<>();
        }
        Map<String, LocalDate> lastBarMap = new HashMap<>();
        Map<String, Integer> barCountMap = new HashMap<>();
        List<Map<String, Object>> stats = baseMapper.selectMaps(Wrappers.<BarDaily>query()
                .select("code", "MAX(trade_date) AS tradeDate", "COUNT(1) AS cnt")
                .in("code", codes)
                .groupBy("code"));
        for (Map<String, Object> row : stats) {
            String code = String.valueOf(row.get("code"));
            Object tradeDate = row.get("tradeDate");
            Object count = row.get("cnt");
            if (Objects.nonNull(tradeDate)) {
                lastBarMap.put(code, LocalDate.parse(String.valueOf(tradeDate).substring(0, 10)));
            }
            if (Objects.nonNull(count)) {
                barCountMap.put(code, Integer.parseInt(String.valueOf(count)));
            }
        }
        LocalDate sessionDay = TradingCalendar.latestTradingDayOnOrBefore(LocalDate.now());
        List<String> staleCodes = new ArrayList<>();
        for (String code : codes) {
            int count = barCountMap.getOrDefault(code, 0);
            LocalDate last = lastBarMap.get(code);
            boolean stale = count < 60 || Objects.isNull(last) || last.isBefore(sessionDay);
            if (stale) {
                staleCodes.add(code);
            }
            if (staleCodes.size() >= limit) {
                break;
            }
        }
        return staleCodes;
    }

    private BarSyncResp emptySyncResponse() {
        return BarSyncResp.builder()
                .source("none")
                .fetchedAt(LocalDateTime.now())
                .successCount(0)
                .failCount(0)
                .barCount(0)
                .details(List.of("无过期/缺失日线需要同步"))
                .build();
    }

    /**
     * 多轮补齐自选缺失/过期日线
     *
     * @param groupName 分组
     * @param rounds    轮数
     * @param limit     每轮上限
     * @return 汇总
     */
    @Override
    public FillBarsResp fillWatchlist(String groupName, Integer rounds, Integer limit) {
        int maxRounds = Objects.isNull(rounds) ? 3 : Math.max(1, Math.min(rounds, 8));
        int perRound = Objects.isNull(limit) ? 40 : Math.max(1, Math.min(limit, MAX_SYNC_CODES));
        int totalSuccess = 0;
        int totalFail = 0;
        int totalBars = 0;
        int ran = 0;
        boolean completed = false;
        for (int i = 0; i < maxRounds; i++) {
            BarSyncResp round = syncStaleWatchlist(groupName, perRound);
            ran++;
            totalSuccess += Objects.nonNull(round.getSuccessCount()) ? round.getSuccessCount() : 0;
            totalFail += Objects.nonNull(round.getFailCount()) ? round.getFailCount() : 0;
            totalBars += Objects.nonNull(round.getBarCount()) ? round.getBarCount() : 0;
            if (Objects.isNull(round.getSuccessCount()) || round.getSuccessCount() == 0) {
                completed = true;
                break;
            }
        }
        return FillBarsResp.builder()
                .rounds(ran)
                .totalSuccess(totalSuccess)
                .totalFail(totalFail)
                .totalBars(totalBars)
                .completed(completed)
                .message(completed ? "已无更多过期/缺失日线" : "已达轮数上限，可再次点击继续补齐")
                .build();
    }

    private BarSyncResp doSync(List<String> codes, String beginDateRaw, String endDateRaw, String scopeDesc) {
        LocalDate end = LocalDate.now();
        // 约 400 自然日 ≈ 260+ 交易日，满足均线/信号回看
        LocalDate begin = end.minusDays(400);
        String beginDate = StringUtils.isNotBlank(beginDateRaw) ? beginDateRaw : begin.toString();
        String endDate = StringUtils.isNotBlank(endDateRaw) ? endDateRaw : end.toString();
        LocalDateTime fetchedAt = LocalDateTime.now();

        ExecutorService pool = Executors.newFixedThreadPool(Math.min(MAX_PARALLEL, codes.size()));
        List<SyncItem> items = new ArrayList<>();
        try {
            List<Callable<SyncItem>> tasks = new ArrayList<>();
            for (String code : codes) {
                tasks.add(() -> syncOne(code, beginDate, endDate));
            }
            List<Future<SyncItem>> futures = pool.invokeAll(
                    tasks, Math.max(syncTimeoutSeconds, 1), TimeUnit.SECONDS);
            for (int index = 0; index < futures.size(); index++) {
                Future<SyncItem> future = futures.get(index);
                String code = codes.get(index);
                if (future.isCancelled()) {
                    items.add(new SyncItem(code, false, 0, code + " TIMEOUT"));
                    continue;
                }
                try {
                    items.add(future.get());
                } catch (Exception ex) {
                    items.add(new SyncItem(code, false, 0, code + " FAIL: " + ex.getMessage()));
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("日线同步被中断", ex);
        } finally {
            pool.shutdownNow();
        }

        int successCount = 0;
        int failCount = 0;
        int barCount = 0;
        List<String> details = new ArrayList<>();
        for (SyncItem item : items) {
            details.add(item.detail);
            if (item.ok) {
                successCount++;
                barCount += item.barCount;
            } else {
                failCount++;
            }
        }

        String status = failCount == 0 ? "SUCCESS" : (successCount == 0 ? "FAIL" : "PARTIAL");
        // 多数走新浪；仅当明细里完全没有新浪成功标记时才记东财
        boolean anySina = details.stream().anyMatch(d -> d.contains("(" + DailyBarClient.SOURCE_SINA + ")"));
        String sourceLabel = anySina ? DailyBarClient.SOURCE_SINA : DailyBarClient.SOURCE_EASTMONEY;
        // 明细可能很长，日志只保留摘要 + 截断后的明细，避免撑爆 message 字段
        String message = buildSyncLogMessage(successCount, failCount, barCount, details);
        DataSyncLog syncLog = DataSyncLog.builder()
                .syncType("BAR_DAILY")
                .source(sourceLabel)
                .scopeDesc(scopeDesc + ", begin=" + beginDate + ", end=" + endDate)
                .status(status)
                .message(message)
                .fetchedAt(fetchedAt)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .deleted(0)
                .build();
        dataSyncLogMapper.insert(syncLog);

        return BarSyncResp.builder()
                .source(sourceLabel)
                .fetchedAt(fetchedAt)
                .successCount(successCount)
                .failCount(failCount)
                .barCount(barCount)
                .details(details)
                .build();
    }

    private String buildSyncLogMessage(int successCount, int failCount, int barCount, List<String> details) {
        String summary = "success=" + successCount + ", fail=" + failCount + ", bars=" + barCount;
        if (details == null || details.isEmpty()) {
            return summary;
        }
        String detailText = String.join("; ", details);
        String full = summary + " | " + detailText;
        int maxLen = 4000;
        if (full.length() <= maxLen) {
            return full;
        }
        return full.substring(0, maxLen) + "...(truncated)";
    }

    private SyncItem syncOne(String code, String beginDate, String endDate) {
        try {
            // 错峰请求，降低行情源限流概率
            Thread.sleep(220L + (Math.abs(code.hashCode()) % 280));
            List<BarDaily> bars = dailyBarClient.fetchDailyBars(code, beginDate, endDate);
            int upserted = upsertBars(bars);
            upsertStockBasic(code, MarketCodeUtils.resolveMarket(code));
            String source = bars.isEmpty() ? "unknown" : bars.get(0).getSource();
            return new SyncItem(code, true, upserted, code + ": " + upserted + " bars (" + source + ")");
        } catch (Exception ex) {
            log.warn("同步日线失败，证券代码={}，异常={}", code, ex.getMessage());
            return new SyncItem(code, false, 0, code + " FAIL: " + ex.getMessage());
        }
    }

    private int upsertBars(List<BarDaily> bars) {
        if (bars == null || bars.isEmpty()) {
            return 0;
        }
        String code = bars.get(0).getCode();
        LocalDate minDate = bars.get(0).getTradeDate();
        LocalDate maxDate = bars.get(0).getTradeDate();
        for (BarDaily bar : bars) {
            if (bar.getTradeDate().isBefore(minDate)) {
                minDate = bar.getTradeDate();
            }
            if (bar.getTradeDate().isAfter(maxDate)) {
                maxDate = bar.getTradeDate();
            }
        }
        List<BarDaily> existingList = list(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, code)
                .between(BarDaily::getTradeDate, minDate, maxDate));
        Map<LocalDate, BarDaily> existingMap = new HashMap<>();
        for (BarDaily existing : existingList) {
            existingMap.put(existing.getTradeDate(), existing);
        }
        List<BarDaily> insertBars = new ArrayList<>();
        List<BarDaily> updateBars = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (BarDaily bar : bars) {
            BarDaily existing = existingMap.get(bar.getTradeDate());
            if (Objects.isNull(existing)) {
                insertBars.add(bar);
            } else {
                // 新浪等源无额/涨跌幅/换手时，保留库内已有值，避免被刷空
                if (Objects.isNull(bar.getAmount()) && Objects.nonNull(existing.getAmount())) {
                    bar.setAmount(existing.getAmount());
                }
                if (Objects.isNull(bar.getPctChg()) && Objects.nonNull(existing.getPctChg())) {
                    bar.setPctChg(existing.getPctChg());
                }
                if (Objects.isNull(bar.getTurnoverRate()) && Objects.nonNull(existing.getTurnoverRate())) {
                    bar.setTurnoverRate(existing.getTurnoverRate());
                }
                bar.setId(existing.getId());
                bar.setCreateTime(existing.getCreateTime());
                bar.setUpdateTime(now);
                updateBars.add(bar);
            }
        }
        if (!insertBars.isEmpty()) {
            saveBatch(insertBars, 200);
        }
        if (!updateBars.isEmpty()) {
            updateBatchById(updateBars, 200);
        }
        return bars.size();
    }

    private void upsertStockBasic(String code, String market) {
        StockBasic existing = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                .eq(StockBasic::getCode, code)
                .last("limit 1"));
        LocalDateTime now = LocalDateTime.now();
        if (Objects.isNull(existing)) {
            stockBasicMapper.insert(StockBasic.builder()
                    .code(code)
                    .market(market)
                    .stFlag(0)
                    .createTime(now)
                    .updateTime(now)
                    .deleted(0)
                    .build());
            return;
        }
        existing.setMarket(market);
        existing.setUpdateTime(now);
        stockBasicMapper.updateById(existing);
    }

    private static final class SyncItem {
        private final String code;
        private final boolean ok;
        private final int barCount;
        private final String detail;

        private SyncItem(String code, boolean ok, int barCount, String detail) {
            this.code = code;
            this.ok = ok;
            this.barCount = barCount;
            this.detail = detail;
        }
    }
}
