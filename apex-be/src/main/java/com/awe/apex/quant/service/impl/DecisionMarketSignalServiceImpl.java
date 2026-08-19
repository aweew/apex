package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.bo.DecisionMarketScanReq;
import com.awe.apex.quant.domain.bo.DecisionMarketSnapshot;
import com.awe.apex.quant.domain.dto.SignalConfluenceItem;
import com.awe.apex.quant.domain.dto.SignalConfluenceResp;
import com.awe.apex.quant.domain.dto.SignalRunReq;
import com.awe.apex.quant.domain.entity.DecisionMarketScan;
import com.awe.apex.quant.domain.entity.DecisionMarketSignal;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.StrategySignalEntity;
import com.awe.apex.quant.mapper.DecisionMarketScanMapper;
import com.awe.apex.quant.mapper.DecisionMarketSignalMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.service.IDecisionMarketSignalService;
import com.awe.apex.quant.service.ISignalService;
import com.awe.apex.quant.service.TaskProgressListener;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 共享市场决策信号服务实现。
 */
@Service
@Slf4j
public class DecisionMarketSignalServiceImpl implements IDecisionMarketSignalService {

    @Resource
    private DecisionMarketScanMapper decisionMarketScanMapper;

    @Resource
    private DecisionMarketSignalMapper decisionMarketSignalMapper;

    @Resource
    private StockBasicMapper stockBasicMapper;

    @Resource
    private ISignalService signalService;

    @Resource
    private TransactionTemplate transactionTemplate;

    /**
     * 刷新指定股票池批次的共享买入扫描。
     *
     * @param req 扫描请求
     * @param progressListener 进度监听器
     * @return 完成的共享市场快照
     */
    @Override
    public DecisionMarketSnapshot refresh(DecisionMarketScanReq req, TaskProgressListener progressListener) {
        if (Objects.isNull(req) || Objects.isNull(req.getActionDate())
                || StringUtils.isBlank(req.getUniverseBatchNo()) || CollUtil.isEmpty(req.getCodes())) {
            throw new BusinessException("共享市场扫描参数不完整");
        }
        boolean includeBj = Boolean.TRUE.equals(req.getIncludeBj());
        LocalDateTime now = LocalDateTime.now();
        DecisionMarketScan scan = decisionMarketScanMapper.selectOne(Wrappers.<DecisionMarketScan>lambdaQuery()
                .eq(DecisionMarketScan::getActionDate, req.getActionDate())
                .eq(DecisionMarketScan::getUniverseBatchNo, req.getUniverseBatchNo())
                .eq(DecisionMarketScan::getIncludeBj, includeBj)
                .last("LIMIT 1"));
        if (Objects.isNull(scan)) {
            scan = DecisionMarketScan.builder()
                    .actionDate(req.getActionDate())
                    .universeBatchNo(req.getUniverseBatchNo())
                    .includeBj(includeBj)
                    .createTime(now)
                    .build();
        }
        scan.setStatus("RUNNING");
        scan.setUniverseCount(Objects.nonNull(req.getUniverseCount()) ? req.getUniverseCount() : req.getCodes().size());
        scan.setHotScanCount(Objects.nonNull(req.getHotScanCount()) ? req.getHotScanCount() : 0);
        scan.setScanCodeCount(req.getCodes().size());
        scan.setSignalCount(0);
        scan.setErrorMessage(null);
        scan.setStartedAt(now);
        scan.setFinishedAt(null);
        scan.setUpdateTime(now);
        if (Objects.isNull(scan.getId())) {
            decisionMarketScanMapper.insert(scan);
        } else {
            decisionMarketScanMapper.updateById(scan);
        }

        try {
            SignalRunReq signalReq = new SignalRunReq();
            signalReq.setAsOfDate(req.getActionDate());
            signalReq.setCodes(req.getCodes());
            signalReq.setSellCodes(Collections.emptyList());
            List<StrategySignalEntity> scanned = signalService.scan(signalReq, progressListener);
            List<StrategySignalEntity> buySignals = new ArrayList<>();
            for (StrategySignalEntity signal : scanned) {
                if (Objects.nonNull(signal) && "BUY".equalsIgnoreCase(signal.getSide())) {
                    buySignals.add(signal);
                }
            }
            DecisionMarketScan completedScan = scan;
            transactionTemplate.executeWithoutResult(status -> {
                decisionMarketSignalMapper.deleteByScanId(completedScan.getId());
                LocalDateTime completedAt = LocalDateTime.now();
                for (StrategySignalEntity signal : buySignals) {
                    decisionMarketSignalMapper.insert(DecisionMarketSignal.builder()
                            .scanId(completedScan.getId())
                            .actionDate(req.getActionDate())
                            .code(signal.getCode())
                            .strategyId(signal.getStrategyId())
                            .signalDate(signal.getSignalDate())
                            .side("BUY")
                            .score(signal.getScore())
                            .reasonJson(signal.getReasonJson())
                            .createTime(completedAt)
                            .updateTime(completedAt)
                            .build());
                }
                completedScan.setStatus("SUCCESS");
                completedScan.setSignalCount(buySignals.size());
                completedScan.setFinishedAt(completedAt);
                completedScan.setUpdateTime(completedAt);
                decisionMarketScanMapper.updateById(completedScan);
            });
            log.info("共享市场信号扫描完成，扫描编号={}，决策日期={}，股票池批次={}，是否包含北交所={}，扫描数量={}，信号数量={}",
                    scan.getId(), scan.getActionDate(), scan.getUniverseBatchNo(), includeBj,
                    scan.getScanCodeCount(), buySignals.size());
            return buildSnapshot(scan, buySignals);
        } catch (RuntimeException ex) {
            scan.setStatus("FAILED");
            scan.setErrorMessage(StringUtils.isNotBlank(ex.getMessage()) ? ex.getMessage() : "共享市场扫描失败");
            scan.setFinishedAt(LocalDateTime.now());
            scan.setUpdateTime(scan.getFinishedAt());
            decisionMarketScanMapper.updateById(scan);
            throw ex;
        }
    }

    /**
     * 读取精确匹配的已完成共享市场快照。
     *
     * @param actionDate 决策日期
     * @param universeBatchNo 股票池批次号
     * @param includeBj 是否包含北交所
     * @return 完成的共享市场快照
     */
    @Override
    public DecisionMarketSnapshot require(LocalDate actionDate, String universeBatchNo, boolean includeBj) {
        DecisionMarketScan scan = decisionMarketScanMapper.selectOne(Wrappers.<DecisionMarketScan>lambdaQuery()
                .eq(DecisionMarketScan::getActionDate, actionDate)
                .eq(DecisionMarketScan::getUniverseBatchNo, universeBatchNo)
                .eq(DecisionMarketScan::getIncludeBj, includeBj)
                .eq(DecisionMarketScan::getStatus, "SUCCESS")
                .last("LIMIT 1"));
        if (Objects.isNull(scan)) {
            throw new BusinessException("当日共享市场决策尚未生成，请等待系统同步完成");
        }
        List<DecisionMarketSignal> storedSignals = decisionMarketSignalMapper.selectList(
                Wrappers.<DecisionMarketSignal>lambdaQuery()
                        .eq(DecisionMarketSignal::getScanId, scan.getId())
                        .orderByDesc(DecisionMarketSignal::getScore));
        return buildSnapshot(scan, toStrategySignals(storedSignals));
    }

    /**
     * 计算共享买入信号共振。
     *
     * @param days 回看天数
     * @param minStrategies 最少策略数
     * @param asOfDate 截止日期
     * @param includeBj 是否包含北交所
     * @return 买入信号共振
     */
    @Override
    public SignalConfluenceResp confluence(int days, int minStrategies, LocalDate asOfDate, boolean includeBj) {
        int safeDays = Math.max(1, Math.min(days, 30));
        int safeMinStrategies = Math.max(2, Math.min(minStrategies, 10));
        LocalDate cutoff = Objects.nonNull(asOfDate) ? asOfDate : LocalDate.now();
        List<DecisionMarketScan> scans = decisionMarketScanMapper.selectList(Wrappers.<DecisionMarketScan>lambdaQuery()
                .eq(DecisionMarketScan::getIncludeBj, includeBj)
                .eq(DecisionMarketScan::getStatus, "SUCCESS")
                .ge(DecisionMarketScan::getActionDate, cutoff.minusDays(safeDays))
                .le(DecisionMarketScan::getActionDate, cutoff));
        if (CollUtil.isEmpty(scans)) {
            return emptyConfluence(safeDays, safeMinStrategies);
        }
        List<Long> scanIds = new ArrayList<>();
        for (DecisionMarketScan scan : scans) {
            scanIds.add(scan.getId());
        }
        List<DecisionMarketSignal> signals = decisionMarketSignalMapper.selectList(
                Wrappers.<DecisionMarketSignal>lambdaQuery()
                        .in(DecisionMarketSignal::getScanId, scanIds)
                        .eq(DecisionMarketSignal::getSide, "BUY")
                        .orderByDesc(DecisionMarketSignal::getId)
                        .last("LIMIT 2000"));

        Map<String, Set<String>> strategyIds = new HashMap<>();
        Map<String, BigDecimal> scoreSums = new HashMap<>();
        Map<String, BigDecimal> maxScores = new HashMap<>();
        Map<String, Integer> scoreCounts = new HashMap<>();
        for (DecisionMarketSignal signal : signals) {
            if (StringUtils.isBlank(signal.getCode()) || StringUtils.isBlank(signal.getStrategyId())) {
                continue;
            }
            strategyIds.computeIfAbsent(signal.getCode(), key -> new HashSet<>()).add(signal.getStrategyId());
            if (Objects.nonNull(signal.getScore())) {
                scoreSums.put(signal.getCode(), scoreSums.getOrDefault(signal.getCode(), BigDecimal.ZERO)
                        .add(signal.getScore()));
                scoreCounts.put(signal.getCode(), scoreCounts.getOrDefault(signal.getCode(), 0) + 1);
                BigDecimal currentMax = maxScores.get(signal.getCode());
                if (Objects.isNull(currentMax) || signal.getScore().compareTo(currentMax) > 0) {
                    maxScores.put(signal.getCode(), signal.getScore());
                }
            }
        }
        List<SignalConfluenceItem> items = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : strategyIds.entrySet()) {
            if (entry.getValue().size() < safeMinStrategies) {
                continue;
            }
            int scoreCount = scoreCounts.getOrDefault(entry.getKey(), 0);
            BigDecimal averageScore = scoreCount == 0 ? BigDecimal.ZERO
                    : scoreSums.getOrDefault(entry.getKey(), BigDecimal.ZERO)
                    .divide(BigDecimal.valueOf(scoreCount), 2, RoundingMode.HALF_UP);
            List<String> strategies = new ArrayList<>(entry.getValue());
            Collections.sort(strategies);
            items.add(SignalConfluenceItem.builder()
                    .code(entry.getKey())
                    .side("BUY")
                    .strategyCount(strategies.size())
                    .strategies(strategies)
                    .avgScore(averageScore)
                    .maxScore(maxScores.getOrDefault(entry.getKey(), BigDecimal.ZERO))
                    .build());
        }
        items.sort((left, right) -> {
            int strategyCompare = Integer.compare(right.getStrategyCount(), left.getStrategyCount());
            return strategyCompare != 0 ? strategyCompare : right.getAvgScore().compareTo(left.getAvgScore());
        });
        if (items.size() > 30) {
            items = new ArrayList<>(items.subList(0, 30));
        }
        fillNames(items);
        return SignalConfluenceResp.builder()
                .days(safeDays)
                .minStrategies(safeMinStrategies)
                .message(items.isEmpty()
                        ? "近" + safeDays + "日无≥" + safeMinStrategies + "策略同向共振"
                        : "近" + safeDays + "日≥" + safeMinStrategies + "策略同向，共 " + items.size() + " 组")
                .items(items)
                .build();
    }

    private DecisionMarketSnapshot buildSnapshot(DecisionMarketScan scan, List<StrategySignalEntity> signals) {
        return DecisionMarketSnapshot.builder()
                .scanId(scan.getId())
                .actionDate(scan.getActionDate())
                .universeBatchNo(scan.getUniverseBatchNo())
                .includeBj(scan.getIncludeBj())
                .universeCount(scan.getUniverseCount())
                .hotScanCount(scan.getHotScanCount())
                .scanCodeCount(scan.getScanCodeCount())
                .signals(signals)
                .build();
    }

    private List<StrategySignalEntity> toStrategySignals(List<DecisionMarketSignal> storedSignals) {
        List<StrategySignalEntity> signals = new ArrayList<>();
        if (CollUtil.isEmpty(storedSignals)) {
            return signals;
        }
        for (DecisionMarketSignal stored : storedSignals) {
            signals.add(StrategySignalEntity.builder()
                    .code(stored.getCode())
                    .strategyId(stored.getStrategyId())
                    .signalDate(stored.getSignalDate())
                    .side(stored.getSide())
                    .score(stored.getScore())
                    .reasonJson(stored.getReasonJson())
                    .build());
        }
        return signals;
    }

    private SignalConfluenceResp emptyConfluence(int days, int minStrategies) {
        return SignalConfluenceResp.builder()
                .days(days)
                .minStrategies(minStrategies)
                .message("近" + days + "日无≥" + minStrategies + "策略同向共振")
                .items(new ArrayList<>())
                .build();
    }

    private void fillNames(List<SignalConfluenceItem> items) {
        if (CollUtil.isEmpty(items)) {
            return;
        }
        Set<String> codes = new HashSet<>();
        for (SignalConfluenceItem item : items) {
            codes.add(item.getCode());
        }
        List<StockBasic> basics = stockBasicMapper.selectList(Wrappers.<StockBasic>lambdaQuery()
                .in(StockBasic::getCode, codes)
                .select(StockBasic::getCode, StockBasic::getName));
        Map<String, String> names = new HashMap<>();
        for (StockBasic basic : basics) {
            names.put(basic.getCode(), basic.getName());
        }
        for (SignalConfluenceItem item : items) {
            item.setName(names.get(item.getCode()));
        }
    }
}
