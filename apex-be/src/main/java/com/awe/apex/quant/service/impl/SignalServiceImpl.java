package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.CodeCountItem;
import com.awe.apex.quant.domain.dto.SignalConfluenceItem;
import com.awe.apex.quant.domain.dto.SignalConfluenceResp;
import com.awe.apex.quant.domain.dto.SignalForwardResp;
import com.awe.apex.quant.domain.dto.SignalItemResp;
import com.awe.apex.quant.domain.dto.SignalRunReq;
import com.awe.apex.quant.domain.dto.SignalScoreBucketItem;
import com.awe.apex.quant.domain.dto.SignalStatsResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.StrategySignalEntity;
import com.awe.apex.quant.domain.entity.UniverseSnapshot;
import com.awe.apex.quant.domain.entity.Watchlist;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.StrategySignalMapper;
import com.awe.apex.quant.mapper.WatchlistMapper;
import com.awe.apex.quant.market.MarketCodeUtils;
import com.awe.apex.quant.service.IPortfolioService;
import com.awe.apex.quant.service.ISignalService;
import com.awe.apex.quant.service.IUniverseService;
import com.awe.apex.quant.service.TaskProgressListener;
import com.awe.apex.quant.strategy.BarSeries;
import com.awe.apex.quant.strategy.Strategy;
import com.awe.apex.quant.strategy.StrategySignalResult;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 策略信号服务实现
 */
@Service
public class SignalServiceImpl implements ISignalService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int LOOKBACK_DAYS = 400;
    private static final int BAR_QUERY_BATCH_SIZE = 40;

    @Resource
    private List<Strategy> strategies;

    @Resource
    private BarDailyMapper barDailyMapper;

    @Resource
    private StrategySignalMapper strategySignalMapper;

    @Resource
    private WatchlistMapper watchlistMapper;

    @Resource
    private StockBasicMapper stockBasicMapper;

    @Resource
    private IUniverseService universeService;

    @Resource
    private IPortfolioService portfolioService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private ApexUserContext userContext;

    /**
     * 运行信号
     *
     * @param req 请求
     * @return 信号列表
     */
    @Override
    public List<StrategySignalEntity> run(SignalRunReq req) {
        return run(req, null);
    }

    /**
     * 运行信号并上报批次进度
     *
     * @param req              请求
     * @param progressListener 进度监听器
     * @return 信号列表
     */
    @Override
    public List<StrategySignalEntity> run(SignalRunReq req, TaskProgressListener progressListener) {
        List<String> buyCodes = resolveCodes(req);
        List<String> configuredSellCodes = Objects.nonNull(req) && Objects.nonNull(req.getSellCodes())
                ? req.getSellCodes() : portfolioService.listActiveHoldingCodes();
        Set<String> buyCodeSet = new HashSet<>();
        List<String> codes = new ArrayList<>();
        for (String code : buyCodes) {
            String normalizedCode = MarketCodeUtils.normalizeHoldingCode(code);
            if (StringUtils.isNotBlank(normalizedCode) && buyCodeSet.add(normalizedCode)) {
                codes.add(normalizedCode);
            }
        }
        Set<String> sellCodeSet = new HashSet<>();
        if (CollUtil.isNotEmpty(configuredSellCodes)) {
            for (String code : configuredSellCodes) {
                String normalizedCode = MarketCodeUtils.normalizeHoldingCode(code);
                if (StringUtils.isNotBlank(normalizedCode) && sellCodeSet.add(normalizedCode)
                        && !buyCodeSet.contains(normalizedCode)) {
                    codes.add(normalizedCode);
                }
            }
        }
        if (CollUtil.isEmpty(codes)) {
            throw new BusinessException("无可用股票代码");
        }
        List<Strategy> selected = selectStrategies(Objects.nonNull(req) ? req.getStrategyIds() : null);
        List<StrategySignalEntity> saved = new ArrayList<>();
        // 按 code|side 各留最高分，避免卖出分更高时把买入机会挤掉
        Map<String, StrategySignalEntity> bestByCodeSide = new HashMap<>();

        // 全市场扫描逐批完成查询与评估，避免百万级日线实体同时驻留 JVM 堆。
        for (int start = 0; start < codes.size(); start += BAR_QUERY_BATCH_SIZE) {
            List<String> codeBatch = codes.subList(start, Math.min(start + BAR_QUERY_BATCH_SIZE, codes.size()));
            Map<String, List<BarDaily>> barsByCode = loadBarsGrouped(
                    codeBatch, Objects.nonNull(req) ? req.getAsOfDate() : null);
            for (String code : codeBatch) {
                List<BarDaily> bars = barsByCode.get(code);
                if (Objects.isNull(bars) || bars.size() < 60) {
                    continue;
                }
                BarSeries series = BarSeries.from(bars);
                for (Strategy strategy : selected) {
                    StrategySignalResult result = strategy.evaluate(code, series);
                    if (Objects.isNull(result)) {
                        continue;
                    }
                    StrategySignalEntity entity = toEntity(result);
                    String side = StringUtils.isNotBlank(entity.getSide()) ? entity.getSide().toUpperCase() : "NA";
                    String normalizedCode = MarketCodeUtils.normalizeHoldingCode(entity.getCode());
                    if ("SELL".equals(side) && !sellCodeSet.contains(normalizedCode)) {
                        continue;
                    }
                    if (!"SELL".equals(side) && !buyCodeSet.contains(normalizedCode)) {
                        continue;
                    }
                    String key = code + "|" + side;
                    StrategySignalEntity existBest = bestByCodeSide.get(key);
                    if (Objects.isNull(existBest) || entity.getScore().compareTo(existBest.getScore()) > 0) {
                        bestByCodeSide.put(key, entity);
                    }
                }
            }
            if (Objects.nonNull(progressListener)) {
                int completed = Math.min(start + codeBatch.size(), codes.size());
                progressListener.onProgress(completed, codes.size(),
                        "正在扫描策略信号 " + completed + "/" + codes.size());
            }
        }

        transactionTemplate.executeWithoutResult(status -> {
            for (StrategySignalEntity entity : bestByCodeSide.values()) {
                // 同代码+策略+信号日去重，避免重复堆积
                strategySignalMapper.delete(Wrappers.<StrategySignalEntity>lambdaQuery()
                        .eq(StrategySignalEntity::getUserId, userContext.currentUserId())
                        .eq(StrategySignalEntity::getCode, entity.getCode())
                        .eq(StrategySignalEntity::getStrategyId, entity.getStrategyId())
                        .eq(StrategySignalEntity::getSignalDate, entity.getSignalDate()));
                strategySignalMapper.insert(entity);
                saved.add(entity);
            }
        });
        saved.sort(Comparator.comparing(StrategySignalEntity::getScore).reversed());
        return saved;
    }

    /**
     * 最近信号
     *
     * @param limit        条数
     * @param dedupeByCode 按代码去重
     * @return 列表
     */
    @Override
    public List<StrategySignalEntity> latest(int limit, boolean dedupeByCode) {
        int size = Math.max(1, Math.min(limit, 200));
        List<StrategySignalEntity> raw = strategySignalMapper.selectList(Wrappers.<StrategySignalEntity>lambdaQuery()
                .eq(StrategySignalEntity::getUserId, userContext.currentUserId())
                .orderByDesc(StrategySignalEntity::getId)
                .last("limit 500"));
        List<StrategySignalEntity> scoped = filterCurrentSellScope(raw);
        if (!dedupeByCode) {
            if (scoped.size() <= size) {
                return scoped;
            }
            return new ArrayList<>(scoped.subList(0, size));
        }
        Map<String, StrategySignalEntity> unique = new HashMap<>();
        List<StrategySignalEntity> result = new ArrayList<>();
        for (StrategySignalEntity item : scoped) {
            if (unique.containsKey(item.getCode())) {
                continue;
            }
            unique.put(item.getCode(), item);
            result.add(item);
            if (result.size() >= size) {
                break;
            }
        }
        return result;
    }

    /**
     * 信号列表补充证券名称
     *
     * @param signals 信号
     * @return 含名称的列表
     */
    @Override
    public List<SignalItemResp> toItemRespList(List<StrategySignalEntity> signals) {
        if (CollUtil.isEmpty(signals)) {
            return new ArrayList<>();
        }
        Set<String> codes = new HashSet<>();
        for (StrategySignalEntity signal : signals) {
            if (StringUtils.isNotBlank(signal.getCode())) {
                codes.add(signal.getCode());
            }
        }
        Map<String, String> nameMap = loadNameMap(codes);
        List<SignalItemResp> result = new ArrayList<>();
        for (StrategySignalEntity signal : signals) {
            result.add(SignalItemResp.builder()
                    .id(signal.getId())
                    .code(signal.getCode())
                    .name(nameMap.get(signal.getCode()))
                    .strategyId(signal.getStrategyId())
                    .signalDate(signal.getSignalDate())
                    .side(signal.getSide())
                    .score(signal.getScore())
                    .reasonJson(signal.getReasonJson())
                    .createTime(signal.getCreateTime())
                    .build());
        }
        return result;
    }

    /**
     * 近 N 日信号统计
     *
     * @param days 天数
     * @return 统计
     */
    @Override
    public SignalStatsResp stats(int days) {
        int n = Math.max(1, Math.min(days, 30));
        List<StrategySignalEntity> list = strategySignalMapper.selectList(Wrappers.<StrategySignalEntity>lambdaQuery()
                .eq(StrategySignalEntity::getUserId, userContext.currentUserId())
                .ge(StrategySignalEntity::getSignalDate, LocalDate.now().minusDays(n))
                .orderByDesc(StrategySignalEntity::getId)
                .last("limit 1000"));
        list = filterCurrentSellScope(list);
        int buy = 0;
        int sell = 0;
        Map<String, Integer> byStrategy = new HashMap<>();
        Map<String, Integer> byCode = new HashMap<>();
        for (StrategySignalEntity item : list) {
            if ("BUY".equalsIgnoreCase(item.getSide())) {
                buy++;
            } else if ("SELL".equalsIgnoreCase(item.getSide())) {
                sell++;
            }
            String sid = StringUtils.isNotBlank(item.getStrategyId()) ? item.getStrategyId() : "NA";
            byStrategy.put(sid, byStrategy.getOrDefault(sid, 0) + 1);
            byCode.put(item.getCode(), byCode.getOrDefault(item.getCode(), 0) + 1);
        }
        List<CodeCountItem> top = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : byCode.entrySet()) {
            top.add(CodeCountItem.builder().code(entry.getKey()).count(entry.getValue()).build());
        }
        top.sort((a, b) -> Integer.compare(b.getCount(), a.getCount()));
        if (top.size() > 10) {
            top = top.subList(0, 10);
        }
        return SignalStatsResp.builder()
                .days(n)
                .total(list.size())
                .buyCount(buy)
                .sellCount(sell)
                .byStrategy(byStrategy)
                .topCodes(top)
                .build();
    }

    /**
     * 多策略共振
     *
     * @param days          天数
     * @param minStrategies 最少策略
     * @return 共振
     */
    @Override
    public SignalConfluenceResp confluence(int days, int minStrategies) {
        return confluence(days, minStrategies, LocalDate.now());
    }

    @Override
    public SignalConfluenceResp confluence(int days, int minStrategies, LocalDate asOfDate) {
        int n = Math.max(1, Math.min(days, 30));
        int minS = Math.max(2, Math.min(minStrategies, 10));
        LocalDate cutoff = Objects.nonNull(asOfDate) ? asOfDate : LocalDate.now();
        List<StrategySignalEntity> list = strategySignalMapper.selectList(Wrappers.<StrategySignalEntity>lambdaQuery()
                .eq(StrategySignalEntity::getUserId, userContext.currentUserId())
                .ge(StrategySignalEntity::getSignalDate, cutoff.minusDays(n))
                .le(StrategySignalEntity::getSignalDate, cutoff)
                .orderByDesc(StrategySignalEntity::getId)
                .last("limit 2000"));
        if (!cutoff.isBefore(LocalDate.now())) {
            list = filterCurrentSellScope(list);
        }
        // key = code|side
        Map<String, Set<String>> strategies = new HashMap<>();
        Map<String, BigDecimal> scoreSum = new HashMap<>();
        Map<String, BigDecimal> scoreMax = new HashMap<>();
        Map<String, Integer> scoreN = new HashMap<>();
        for (StrategySignalEntity signal : list) {
            if (StringUtils.isBlank(signal.getCode()) || StringUtils.isBlank(signal.getSide())) {
                continue;
            }
            String side = signal.getSide().toUpperCase();
            if (!"BUY".equals(side) && !"SELL".equals(side)) {
                continue;
            }
            String key = signal.getCode() + "|" + side;
            String sid = StringUtils.isNotBlank(signal.getStrategyId()) ? signal.getStrategyId() : "NA";
            strategies.computeIfAbsent(key, k -> new HashSet<>()).add(sid);
            if (Objects.nonNull(signal.getScore())) {
                scoreSum.put(key, scoreSum.getOrDefault(key, BigDecimal.ZERO).add(signal.getScore()));
                scoreN.put(key, scoreN.getOrDefault(key, 0) + 1);
                BigDecimal curMax = scoreMax.get(key);
                if (Objects.isNull(curMax) || signal.getScore().compareTo(curMax) > 0) {
                    scoreMax.put(key, signal.getScore());
                }
            }
        }
        List<SignalConfluenceItem> items = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : strategies.entrySet()) {
            if (entry.getValue().size() < minS) {
                continue;
            }
            String[] parts = entry.getKey().split("\\|", 2);
            int sn = scoreN.getOrDefault(entry.getKey(), 0);
            BigDecimal avg = sn == 0 ? BigDecimal.ZERO
                    : scoreSum.getOrDefault(entry.getKey(), BigDecimal.ZERO)
                    .divide(BigDecimal.valueOf(sn), 2, RoundingMode.HALF_UP);
            List<String> sids = new ArrayList<>(entry.getValue());
            Collections.sort(sids);
            items.add(SignalConfluenceItem.builder()
                    .code(parts[0])
                    .side(parts[1])
                    .strategyCount(entry.getValue().size())
                    .strategies(sids)
                    .avgScore(avg)
                    .maxScore(scoreMax.getOrDefault(entry.getKey(), BigDecimal.ZERO))
                    .build());
        }
        items.sort((a, b) -> {
            int byCnt = Integer.compare(b.getStrategyCount(), a.getStrategyCount());
            if (byCnt != 0) {
                return byCnt;
            }
            return b.getAvgScore().compareTo(a.getAvgScore());
        });
        if (items.size() > 30) {
            items = items.subList(0, 30);
        }
        Set<String> codes = new HashSet<>();
        for (SignalConfluenceItem item : items) {
            codes.add(item.getCode());
        }
        Map<String, String> nameMap = loadNameMap(codes);
        for (SignalConfluenceItem item : items) {
            item.setName(nameMap.get(item.getCode()));
        }
        return SignalConfluenceResp.builder()
                .days(n)
                .minStrategies(minS)
                .message(items.isEmpty()
                        ? "近" + n + "日无≥" + minS + "策略同向共振"
                        : "近" + n + "日≥" + minS + "策略同向，共 " + items.size() + " 组")
                .items(items)
                .build();
    }

    /**
     * 信号前瞻收益评估
     *
     * @param lookbackDays 回看天数
     * @param horizonDays  前瞻交易日
     * @return 统计
     */
    @Override
    public SignalForwardResp forwardEval(int lookbackDays, int horizonDays) {
        int lookback = Math.max(5, Math.min(lookbackDays, 120));
        int horizon = Math.max(1, Math.min(horizonDays, 20));
        List<StrategySignalEntity> signals = strategySignalMapper.selectList(Wrappers.<StrategySignalEntity>lambdaQuery()
                .eq(StrategySignalEntity::getUserId, userContext.currentUserId())
                .ge(StrategySignalEntity::getSignalDate, LocalDate.now().minusDays(lookback))
                .le(StrategySignalEntity::getSignalDate, LocalDate.now().minusDays(horizon))
                .orderByDesc(StrategySignalEntity::getId)
                .last("limit 500"));
        if (CollUtil.isEmpty(signals)) {
            return SignalForwardResp.builder()
                    .horizonDays(horizon)
                    .sampleCount(0)
                    .hitRate(BigDecimal.ZERO)
                    .avgForwardReturn(BigDecimal.ZERO)
                    .medianForwardReturn(BigDecimal.ZERO)
                    .buyCount(0)
                    .sellCount(0)
                    .scoreBuckets(Collections.emptyList())
                    .message("无足够历史信号可评估")
                    .build();
        }
        Set<String> codes = new HashSet<>();
        for (StrategySignalEntity signal : signals) {
            if (StringUtils.isNotBlank(signal.getCode())) {
                codes.add(signal.getCode());
            }
        }
        Map<String, List<BarDaily>> barsByCode = loadBarsGrouped(new ArrayList<>(codes), null);
        List<BigDecimal> signedRets = new ArrayList<>();
        int hits = 0;
        int buyCnt = 0;
        int sellCnt = 0;
        String[] bucketLabels = {"<60", "60-70", "70-80", "80+"};
        int[] bucketCnt = new int[4];
        int[] bucketHits = new int[4];
        BigDecimal[] bucketSum = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        for (StrategySignalEntity signal : signals) {
            List<BarDaily> bars = barsByCode.get(signal.getCode());
            if (CollUtil.isEmpty(bars) || Objects.isNull(signal.getSignalDate())) {
                continue;
            }
            int idx = -1;
            for (int i = 0; i < bars.size(); i++) {
                if (signal.getSignalDate().equals(bars.get(i).getTradeDate())) {
                    idx = i;
                    break;
                }
            }
            if (idx < 0) {
                for (int i = 0; i < bars.size(); i++) {
                    if (!bars.get(i).getTradeDate().isBefore(signal.getSignalDate())) {
                        idx = i;
                        break;
                    }
                }
            }
            if (idx < 0 || idx + horizon >= bars.size()) {
                continue;
            }
            BigDecimal p0 = bars.get(idx).getClosePrice();
            BigDecimal p1 = bars.get(idx + horizon).getClosePrice();
            if (Objects.isNull(p0) || p0.signum() <= 0 || Objects.isNull(p1)) {
                continue;
            }
            BigDecimal raw = p1.subtract(p0).divide(p0, 6, RoundingMode.HALF_UP);
            boolean buy = "BUY".equalsIgnoreCase(signal.getSide());
            boolean sell = "SELL".equalsIgnoreCase(signal.getSide());
            if (!buy && !sell) {
                continue;
            }
            BigDecimal signed = buy ? raw : raw.negate();
            signedRets.add(signed);
            if (signed.signum() > 0) {
                hits++;
            }
            if (buy) {
                buyCnt++;
            } else {
                sellCnt++;
            }
            int bIdx = scoreBucketIndex(signal.getScore());
            bucketCnt[bIdx]++;
            bucketSum[bIdx] = bucketSum[bIdx].add(signed);
            if (signed.signum() > 0) {
                bucketHits[bIdx]++;
            }
        }
        if (signedRets.isEmpty()) {
            return SignalForwardResp.builder()
                    .horizonDays(horizon)
                    .sampleCount(0)
                    .hitRate(BigDecimal.ZERO)
                    .avgForwardReturn(BigDecimal.ZERO)
                    .medianForwardReturn(BigDecimal.ZERO)
                    .buyCount(0)
                    .sellCount(0)
                    .scoreBuckets(Collections.emptyList())
                    .message("K线不足，无法评估前瞻收益")
                    .build();
        }
        BigDecimal sum = signedRets.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = sum.divide(BigDecimal.valueOf(signedRets.size()), 6, RoundingMode.HALF_UP);
        List<BigDecimal> sorted = new ArrayList<>(signedRets);
        Collections.sort(sorted);
        BigDecimal median = sorted.get(sorted.size() / 2);
        BigDecimal hitRate = BigDecimal.valueOf(hits)
                .divide(BigDecimal.valueOf(signedRets.size()), 4, RoundingMode.HALF_UP);
        List<SignalScoreBucketItem> buckets = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            BigDecimal bHit = bucketCnt[i] == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(bucketHits[i]).divide(BigDecimal.valueOf(bucketCnt[i]), 4, RoundingMode.HALF_UP);
            BigDecimal bAvg = bucketCnt[i] == 0 ? BigDecimal.ZERO
                    : bucketSum[i].divide(BigDecimal.valueOf(bucketCnt[i]), 6, RoundingMode.HALF_UP);
            buckets.add(SignalScoreBucketItem.builder()
                    .bucket(bucketLabels[i])
                    .sampleCount(bucketCnt[i])
                    .hitRate(bHit)
                    .avgForwardReturn(bAvg)
                    .build());
        }
        return SignalForwardResp.builder()
                .horizonDays(horizon)
                .sampleCount(signedRets.size())
                .hitRate(hitRate)
                .avgForwardReturn(avg)
                .medianForwardReturn(median)
                .buyCount(buyCnt)
                .sellCount(sellCnt)
                .scoreBuckets(buckets)
                .message("回看" + lookback + "日 · 前瞻" + horizon + "交易日 · 样本" + signedRets.size())
                .build();
    }

    private int scoreBucketIndex(BigDecimal score) {
        if (Objects.isNull(score)) {
            return 0;
        }
        if (score.compareTo(new BigDecimal("80")) >= 0) {
            return 3;
        }
        if (score.compareTo(new BigDecimal("70")) >= 0) {
            return 2;
        }
        if (score.compareTo(new BigDecimal("60")) >= 0) {
            return 1;
        }
        return 0;
    }

    private Map<String, List<BarDaily>> loadBarsGrouped(List<String> codes, LocalDate asOfDate) {
        Map<String, List<BarDaily>> map = new HashMap<>();
        if (CollUtil.isEmpty(codes)) {
            return map;
        }
        LocalDate cutoff = Objects.nonNull(asOfDate) ? asOfDate : LocalDate.now();
        LocalDate begin = cutoff.minusDays(LOOKBACK_DAYS);
        // 分批 IN 查询，避免单次过大
        for (int i = 0; i < codes.size(); i += BAR_QUERY_BATCH_SIZE) {
            List<String> batch = codes.subList(i, Math.min(i + BAR_QUERY_BATCH_SIZE, codes.size()));
            List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                    .in(BarDaily::getCode, batch)
                    .ge(BarDaily::getTradeDate, begin)
                    .le(BarDaily::getTradeDate, cutoff)
                    .orderByAsc(BarDaily::getCode)
                    .orderByAsc(BarDaily::getTradeDate));
            for (BarDaily bar : bars) {
                map.computeIfAbsent(bar.getCode(), k -> new ArrayList<>()).add(bar);
            }
        }
        return map;
    }

    private List<String> resolveCodes(SignalRunReq req) {
        List<String> codes = new ArrayList<>();
        if (Objects.nonNull(req) && CollUtil.isNotEmpty(req.getCodes())) {
            for (String code : req.getCodes()) {
                codes.add(MarketCodeUtils.normalizeCode(code));
            }
            return codes;
        }
        if (Objects.nonNull(req) && Boolean.TRUE.equals(req.getUseUniverse())) {
            List<UniverseSnapshot> universe = universeService.latest();
            if (CollUtil.isNotEmpty(universe)) {
                for (UniverseSnapshot item : universe) {
                    codes.add(item.getCode());
                }
                return codes;
            }
        }
        List<Watchlist> watchlists = watchlistMapper.selectList(Wrappers.<Watchlist>lambdaQuery()
                .eq(Watchlist::getUserId, userContext.currentUserId()));
        for (Watchlist item : watchlists) {
            codes.add(item.getCode());
        }
        return codes;
    }

    private List<StrategySignalEntity> filterCurrentSellScope(List<StrategySignalEntity> signals) {
        if (CollUtil.isEmpty(signals)) {
            return new ArrayList<>();
        }
        Set<String> holdingCodes = new HashSet<>(portfolioService.listActiveHoldingCodes());
        List<StrategySignalEntity> scoped = new ArrayList<>();
        for (StrategySignalEntity signal : signals) {
            if (Objects.isNull(signal)) {
                continue;
            }
            if (!"SELL".equalsIgnoreCase(signal.getSide())) {
                scoped.add(signal);
                continue;
            }
            String normalizedCode = MarketCodeUtils.normalizeHoldingCode(signal.getCode());
            if (holdingCodes.contains(normalizedCode)) {
                scoped.add(signal);
            }
        }
        return scoped;
    }

    private List<Strategy> selectStrategies(List<String> strategyIds) {
        if (CollUtil.isEmpty(strategyIds)) {
            return strategies;
        }
        List<Strategy> selected = new ArrayList<>();
        for (Strategy strategy : strategies) {
            if (strategyIds.contains(strategy.strategyId())) {
                selected.add(strategy);
            }
        }
        return selected;
    }

    private Map<String, String> loadNameMap(Set<String> codes) {
        Map<String, String> nameMap = new HashMap<>();
        if (CollUtil.isEmpty(codes)) {
            return nameMap;
        }
        List<StockBasic> basics = stockBasicMapper.selectList(Wrappers.<StockBasic>lambdaQuery()
                .in(StockBasic::getCode, codes)
                .select(StockBasic::getCode, StockBasic::getName));
        for (StockBasic basic : basics) {
            if (StringUtils.isNotBlank(basic.getCode())) {
                nameMap.put(basic.getCode(), basic.getName());
            }
        }
        return nameMap;
    }

    private StrategySignalEntity toEntity(StrategySignalResult result) {
        String reasonJson;
        try {
            reasonJson = OBJECT_MAPPER.writeValueAsString(result.getReason());
        } catch (Exception ex) {
            reasonJson = StringUtils.EMPTY;
        }
        LocalDateTime now = LocalDateTime.now();
        return StrategySignalEntity.builder()
                .userId(userContext.currentUserId())
                .code(result.getCode())
                .strategyId(result.getStrategyId())
                .signalDate(result.getSignalDate())
                .side(result.getSide().getCode())
                .score(result.getScore())
                .reasonJson(reasonJson)
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
    }
}
