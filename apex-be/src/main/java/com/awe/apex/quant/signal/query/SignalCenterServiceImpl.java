package com.awe.apex.quant.signal.query;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.JsonUtils;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.signal.event.MarketBehaviorDetector;
import com.awe.apex.quant.signal.event.SignalCalculationRunWriteBO;
import com.awe.apex.quant.signal.event.SignalDefinitionRuleBO;
import com.awe.apex.quant.signal.event.SignalDetectionResult;
import com.awe.apex.quant.signal.event.SignalEventWriteBO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 市场行为信号中心服务实现。
 */
@Slf4j
@Service
public class SignalCenterServiceImpl implements SignalCenterService {

    @Resource
    private SignalCenterMapper signalCenterMapper;

    @Resource
    private MarketBehaviorDetector marketBehaviorDetector;

    /**
     * 执行市场行为计算并写入事件、快照和生命周期。
     *
     * @param request 计算请求
     * @return 批次状态
     */
    @Override
    public SignalCalculationRunResp calculate(SignalCalculationReq request) {
        if (!"DAY".equalsIgnoreCase(request.getTimeframe())) {
            throw new BusinessException("MVP当前仅支持完整日线计算");
        }
        LocalDate asOfDate = Objects.nonNull(request.getAsOfTime())
                ? request.getAsOfTime().toLocalDate() : signalCenterMapper.selectLatestTradeDate();
        if (Objects.isNull(asOfDate)) {
            throw new BusinessException("没有可用于计算的完整日线");
        }

        // 1. 解析计算范围和当前生效规则。
        List<String> symbols = resolveSymbols(request);
        List<SignalDefinitionRuleBO> definitionRules = signalCenterMapper.selectActiveDefinitionRules();
        if (definitionRules.isEmpty()) {
            throw new BusinessException("市场行为规则尚未初始化");
        }

        // 2. 建立可追踪批次，单证券失败不阻断其余证券。
        String runNo = "SC-" + UUID.randomUUID().toString().replace("-", "");
        SignalCalculationRunWriteBO run = SignalCalculationRunWriteBO.builder()
                .runNo(runNo)
                .triggerType(request.getTriggerType().toUpperCase())
                .timeframe("DAY")
                .asOfTime(asOfDate.atTime(15, 0))
                .totalCount(symbols.size())
                .build();
        signalCenterMapper.insertCalculationRun(run);
        int successCount = 0;
        int failureCount = 0;
        String firstError = null;
        for (String symbol : symbols) {
            try {
                calculateSymbol(symbol, asOfDate, run, definitionRules);
                successCount++;
            } catch (Exception exception) {
                failureCount++;
                if (Objects.isNull(firstError)) {
                    firstError = symbol + ": " + exception.getMessage();
                }
                log.error("市场行为计算失败，批次号={}，证券代码={}，截止日期={}",
                        runNo, symbol, asOfDate, exception);
            }
        }

        // 3. 完成批次并返回真实进度。
        String status = failureCount == 0 ? "SUCCESS" : successCount > 0 ? "PARTIAL" : "FAILED";
        signalCenterMapper.finishCalculationRun(run.getId(), status, successCount, failureCount, firstError);
        return signalCenterMapper.selectCalculationRun(runNo);
    }

    /**
     * 查询计算批次。
     *
     * @param runNo 批次号
     * @return 批次状态
     */
    @Override
    public SignalCalculationRunResp getCalculation(String runNo) {
        SignalCalculationRunResp run = signalCenterMapper.selectCalculationRun(runNo);
        if (Objects.isNull(run)) {
            throw new BusinessException("信号计算批次不存在");
        }
        return run;
    }

    /**
     * 查询信号中心概览。
     *
     * @param timeframe 周期
     * @return 概览
     */
    @Override
    public SignalOverviewResp overview(String timeframe) {
        SignalOverviewResp overview = signalCenterMapper.selectOverview(normalizeTimeframe(timeframe));
        if (Objects.isNull(overview) || Objects.isNull(overview.getDataAsOf())) {
            return SignalOverviewResp.builder()
                    .bullishCount(0L)
                    .bearishCount(0L)
                    .riskCount(0L)
                    .confirmedCount(0L)
                    .dataStatus("EMPTY")
                    .featureVersion("daily-v1")
                    .build();
        }
        return overview;
    }

    /**
     * 查询市场行为排行。
     *
     * @param timeframe 周期
     * @param direction 方向
     * @param lifecycleState 生命周期状态
     * @param minStrength 最低强度
     * @param size 数量
     * @return 排行列表
     */
    @Override
    public List<SignalRankingItemResp> rankings(String timeframe, String direction, String lifecycleState,
                                                BigDecimal minStrength, Integer size) {
        int pageSize = Objects.isNull(size) ? 50 : Math.max(1, Math.min(size, 200));
        return signalCenterMapper.selectRankings(normalizeTimeframe(timeframe), direction,
                lifecycleState, minStrength, pageSize);
    }

    /**
     * 查询个股当前市场行为快照。
     *
     * @param symbol 证券代码
     * @param timeframe 周期
     * @return 个股快照
     */
    @Override
    public SignalStockSnapshotResp stockSnapshot(String symbol, String timeframe) {
        List<SignalRankingItemResp> signals = signalCenterMapper.selectStockSignals(symbol,
                normalizeTimeframe(timeframe));
        List<SignalRankingItemResp> activeSignals = new ArrayList<>();
        List<SignalRankingItemResp> riskSignals = new ArrayList<>();
        int bullishCount = 0;
        int bearishCount = 0;
        for (SignalRankingItemResp signal : signals) {
            if ("RISK".equals(signal.getDirection())) {
                riskSignals.add(signal);
            } else {
                activeSignals.add(signal);
                if ("BULLISH".equals(signal.getDirection())) {
                    bullishCount++;
                } else if ("BEARISH".equals(signal.getDirection())) {
                    bearishCount++;
                }
            }
        }
        String marketState = bullishCount > bearishCount ? "ADVANCING"
                : bearishCount > bullishCount ? "DECLINING" : "TRANSITION";
        SignalRankingItemResp firstSignal = signals.isEmpty() ? null : signals.get(0);
        return SignalStockSnapshotResp.builder()
                .symbol(symbol)
                .name(Objects.nonNull(firstSignal) ? firstSignal.getName() : null)
                .timeframe(normalizeTimeframe(timeframe))
                .marketState(marketState)
                .activeSignals(activeSignals)
                .riskSignals(riskSignals)
                .dataAsOf(Objects.nonNull(firstSignal) ? firstSignal.getDataAsOf() : null)
                .freshness(Objects.nonNull(firstSignal) ? firstSignal.getDataStatus() : "EMPTY")
                .usageHint("先核对数据日期和证据；行为信号不等同于买卖建议")
                .build();
    }

    /**
     * 查询个股生命周期时间轴。
     *
     * @param symbol 证券代码
     * @param timeframe 周期
     * @param size 数量
     * @return 时间轴
     */
    @Override
    public List<SignalTimelineItemResp> timeline(String symbol, String timeframe, Integer size) {
        int pageSize = Objects.isNull(size) ? 50 : Math.max(1, Math.min(size, 200));
        return signalCenterMapper.selectTimeline(symbol, normalizeTimeframe(timeframe), pageSize);
    }

    /**
     * 查询启用信号定义。
     *
     * @return 信号定义
     */
    @Override
    public List<SignalDefinitionResp> definitions() {
        return signalCenterMapper.selectDefinitions();
    }

    private List<String> resolveSymbols(SignalCalculationReq request) {
        if ("ALL".equalsIgnoreCase(request.getScopeType())) {
            return signalCenterMapper.selectUniverseSymbols();
        }
        List<String> symbols = new ArrayList<>();
        if (Objects.nonNull(request.getSymbols())) {
            for (String symbol : request.getSymbols()) {
                if (StringUtils.isNotBlank(symbol)) {
                    symbols.add(symbol.trim());
                }
            }
        }
        if (symbols.isEmpty()) {
            throw new BusinessException("指定范围至少需要一个证券代码");
        }
        return symbols;
    }

    private void calculateSymbol(String symbol, LocalDate asOfDate, SignalCalculationRunWriteBO run,
                                 List<SignalDefinitionRuleBO> definitionRules) {
        List<BarDaily> bars = signalCenterMapper.selectBehaviorBars(symbol, asOfDate, 80);
        List<SignalDetectionResult> detectedSignals = marketBehaviorDetector.detect(symbol, bars, asOfDate);
        for (SignalDetectionResult detectedSignal : detectedSignals) {
            SignalDefinitionRuleBO definitionRule = findDefinitionRule(definitionRules,
                    detectedSignal.getSignalCode());
            if (Objects.isNull(definitionRule)) {
                log.warn("跳过未发布的市场行为，证券代码={}，信号编码={}", symbol,
                        detectedSignal.getSignalCode());
                continue;
            }
            SignalEventWriteBO event = SignalEventWriteBO.builder()
                    .eventNo("EVT-" + symbol + "-" + detectedSignal.getSignalCode() + "-"
                            + asOfDate.toString().replace("-", ""))
                    .definitionId(definitionRule.getDefinitionId())
                    .ruleId(definitionRule.getRuleId())
                    .calculationRunId(run.getId())
                    .symbol(symbol)
                    .timeframe("DAY")
                    .triggerTime(detectedSignal.getAsOfTime())
                    .asOfTime(detectedSignal.getAsOfTime())
                    .direction(detectedSignal.getDirection())
                    .lifecycleState(detectedSignal.getLifecycleState())
                    .strength(detectedSignal.getStrength())
                    .confidence(detectedSignal.getConfidence())
                    .riskScore(detectedSignal.getRiskScore())
                    .validUntil(detectedSignal.getAsOfTime().plusDays(5))
                    .evidenceJson(JsonUtils.toJsonString(detectedSignal.getEvidence()))
                    .dataStatus(detectedSignal.getDataStatus())
                    .featureVersion(detectedSignal.getFeatureVersion())
                    .build();
            signalCenterMapper.insertEvent(event);
            if (Objects.isNull(event.getId())) {
                event.setId(signalCenterMapper.selectEventId(symbol, "DAY", event.getDefinitionId(),
                        event.getRuleId(), event.getTriggerTime()));
            }
            if (Objects.nonNull(event.getId())) {
                signalCenterMapper.upsertSnapshot(event);
                signalCenterMapper.insertInitialLifecycle(event);
            }
        }
    }

    private SignalDefinitionRuleBO findDefinitionRule(List<SignalDefinitionRuleBO> definitionRules,
                                                       String signalCode) {
        for (SignalDefinitionRuleBO definitionRule : definitionRules) {
            if (signalCode.equals(definitionRule.getSignalCode())) {
                return definitionRule;
            }
        }
        return null;
    }

    private String normalizeTimeframe(String timeframe) {
        return StringUtils.isBlank(timeframe) ? "DAY" : timeframe.toUpperCase();
    }
}
