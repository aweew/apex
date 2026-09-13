package com.awe.apex.quant.signal.query;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.JsonUtils;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.ExternalMarketItemResp;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.dto.MarketIndexItem;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.market.ExternalMarketQuoteClient;
import com.awe.apex.quant.signal.event.MarketBehaviorDetector;
import com.awe.apex.quant.signal.event.SignalCalculationRunWriteBO;
import com.awe.apex.quant.signal.event.SignalDefinitionRuleBO;
import com.awe.apex.quant.signal.event.SignalEvidence;
import com.awe.apex.quant.signal.event.SignalDetectionResult;
import com.awe.apex.quant.signal.event.SignalEventWriteBO;
import com.awe.apex.quant.service.IMarketBriefingService;
import com.awe.apex.quant.signal.mapper.SignalCenterMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @Resource
    private IMarketBriefingService marketBriefingService;

    @Resource
    private ExternalMarketQuoteClient externalMarketQuoteClient;

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

    /**
     * 查询短线市场上下文、放量回踩候选和策略剧本。
     *
     * @return 短线信号页聚合结果
     */
    @Override
    public ShortTermSignalResp shortTerm() {
        MarketBriefingResp market = marketBriefingService.briefing();
        LocalDate dataAsOf = Objects.nonNull(market) && Objects.nonNull(market.getAsOf())
                ? market.getAsOf() : signalCenterMapper.selectLatestTradeDate();
        List<String> missingData = new ArrayList<>();
        if (Objects.isNull(market)) {
            missingData.add("A股市场简报");
        }

        ShortTermEmotionResp ashareEmotion = buildAshareEmotion(market, dataAsOf, missingData);
        List<ExternalMarketItemResp> macroItems = externalMarketQuoteClient.fetch();
        if (Objects.isNull(macroItems)) {
            macroItems = List.of();
        }
        if (macroItems.stream().anyMatch(item -> Objects.isNull(item) || !item.isAvailable())) {
            missingData.add("部分外围宏观指标");
        }
        ShortTermVixResp externalEmotion = buildVixEmotion(externalMarketQuoteClient.fetchVixProxy(), missingData);

        Map<String, SignalUniverseItemResp> universe = new LinkedHashMap<>();
        for (SignalUniverseItemResp item : signalCenterMapper.selectUniverseItems()) {
            if (Objects.nonNull(item) && StringUtils.isNotBlank(item.getSymbol())) {
                universe.put(item.getSymbol(), item);
            }
        }
        List<BarDaily> allBars = Objects.nonNull(dataAsOf)
                ? signalCenterMapper.selectShortTermBars(dataAsOf, 80) : List.of();
        Map<String, List<BarDaily>> barsBySymbol = new LinkedHashMap<>();
        for (BarDaily bar : allBars) {
            if (Objects.nonNull(bar) && StringUtils.isNotBlank(bar.getCode())) {
                barsBySymbol.computeIfAbsent(bar.getCode(), key -> new ArrayList<>()).add(bar);
            }
        }
        List<ShortTermCandidateResp> candidates = new ArrayList<>();
        for (Map.Entry<String, List<BarDaily>> entry : barsBySymbol.entrySet()) {
            SignalDetectionResult result = marketBehaviorDetector.detectVolumePullbackCandidate(
                    entry.getKey(), entry.getValue(), dataAsOf);
            if (Objects.isNull(result)) {
                continue;
            }
            SignalUniverseItemResp stock = universe.get(entry.getKey());
            candidates.add(toCandidate(result, stock));
        }
        candidates.sort(Comparator.comparing(item -> "CONFIRMED".equals(item.getState()) ? 0 : 1));
        if (candidates.size() > 50) {
            candidates = new ArrayList<>(candidates.subList(0, 50));
        }

        ShortTermSignalResp response = ShortTermSignalResp.builder()
                .dataAsOf(dataAsOf)
                .ashareEmotion(ashareEmotion)
                .indexes(Objects.nonNull(market) && Objects.nonNull(market.getIndexes())
                        ? market.getIndexes() : List.of())
                .shanghaiKeyResistance(Objects.nonNull(market) ? market.getShanghaiKeyResistance() : null)
                .shanghaiCurrentPrice(findIndexClose(market, "上证"))
                .indexVolume(Objects.nonNull(market) ? market.getIndexVolume() : null)
                .indexVolumeText(Objects.nonNull(market) ? market.getIndexVolumeText() : null)
                .volumeTrend(Objects.nonNull(market) ? market.getVolumeTrend() : null)
                .volumeChangePct(Objects.nonNull(market) ? market.getVolumeVsMa5Pct() : null)
                .breadthUp(Objects.nonNull(market) ? market.getBreadthUp() : null)
                .breadthDown(Objects.nonNull(market) ? market.getBreadthDown() : null)
                .breadthFlat(Objects.nonNull(market) ? market.getBreadthFlat() : null)
                .macroItems(macroItems)
                .externalEmotion(externalEmotion)
                .candidates(candidates)
                .build();
        response.setResistanceDistancePct(resolveResistanceDistance(
                response.getShanghaiCurrentPrice(), response.getShanghaiKeyResistance()));
        response.setResistanceAdvice(resolveResistanceAdvice(response));
        response.setStrategy(buildStrategy(response));
        response.setMissingData(missingData);
        response.setDataStatus(missingData.isEmpty() ? "COMPLETE" : "PARTIAL");
        return response;
    }

    private ShortTermEmotionResp buildAshareEmotion(MarketBriefingResp market, LocalDate dataAsOf,
                                                    List<String> missingData) {
        if (Objects.isNull(market) || Objects.isNull(market.getStanceScore())) {
            missingData.add("A股情绪温度");
            return ShortTermEmotionResp.builder()
                    .dataAsOf(dataAsOf)
                    .label("未知")
                    .basis("A股情绪温度暂未形成")
                    .build();
        }
        int score = Math.max(0, Math.min(100, market.getStanceScore()));
        return ShortTermEmotionResp.builder()
                .score(score)
                .label(emotionLabel(score))
                .dataAsOf(dataAsOf)
                .basis(StringUtils.isNotBlank(market.getStanceReason())
                        ? market.getStanceReason() : "由指数、量能、广度、涨跌停和赚钱效应综合计算")
                .build();
    }

    private ShortTermVixResp buildVixEmotion(ExternalMarketItemResp item, List<String> missingData) {
        if (Objects.isNull(item) || !item.isAvailable() || Objects.isNull(item.getLatestPrice())) {
            missingData.add("VIX外部情绪代理");
            return ShortTermVixResp.builder()
                    .available(false)
                    .label("未知")
                    .note("VIX未获取，暂不据此判断外部风险偏好")
                    .build();
        }
        BigDecimal value = item.getLatestPrice();
        int score = value.subtract(new BigDecimal("10"))
                .multiply(new BigDecimal("100"))
                .divide(new BigDecimal("30"), 0, RoundingMode.HALF_UP)
                .negate()
                .add(new BigDecimal("100"))
                .max(BigDecimal.ZERO)
                .min(new BigDecimal("100"))
                .intValue();
        return ShortTermVixResp.builder()
                .available(true)
                .value(value)
                .score(score)
                .label(emotionLabel(score))
                .quoteTime(item.getQuoteTime())
                .source(item.getSource())
                .note("VIX反向代理，不是官方恐贪指数")
                .build();
    }

    private ShortTermCandidateResp toCandidate(SignalDetectionResult result, SignalUniverseItemResp stock) {
        SignalEvidence evidence = result.getEvidence();
        return ShortTermCandidateResp.builder()
                .symbol(Objects.nonNull(stock) ? stock.getSymbol() : null)
                .name(Objects.nonNull(stock) ? stock.getName() : null)
                .market(Objects.nonNull(stock) ? stock.getMarket() : null)
                .signalCode(result.getSignalCode())
                .state(result.getLifecycleState())
                .dataAsOf(result.getAsOfTime().toLocalDate())
                .breakoutPrice(evidence.getBreakoutPrice())
                .pullbackPrice(evidence.getPullbackPrice())
                .currentPrice(evidence.getClosePrice())
                .distancePct(evidence.getDistancePct())
                .atr14(evidence.getAtr14())
                .volumeRatio(evidence.getVolumeRatio())
                .closePosition(evidence.getClosePosition())
                .triggerCondition(evidence.getTriggerCondition())
                .invalidCondition(evidence.getInvalidCondition())
                .build();
    }

    private String emotionLabel(int score) {
        if (score <= 35) {
            return "恐慌";
        }
        if (score >= 65) {
            return "贪婪";
        }
        return "中性";
    }

    private BigDecimal findIndexClose(MarketBriefingResp market, String namePart) {
        if (Objects.isNull(market) || Objects.isNull(market.getIndexes())) {
            return null;
        }
        for (MarketIndexItem item : market.getIndexes()) {
            if (Objects.nonNull(item) && StringUtils.isNotBlank(item.getName())
                    && item.getName().contains(namePart)) {
                return item.getClose();
            }
        }
        return null;
    }

    private BigDecimal resolveResistanceDistance(BigDecimal currentPrice, BigDecimal resistance) {
        if (Objects.isNull(currentPrice) || Objects.isNull(resistance)
                || currentPrice.signum() <= 0 || resistance.signum() <= 0) {
            return null;
        }
        return resistance.subtract(currentPrice)
                .multiply(new BigDecimal("100"))
                .divide(currentPrice, 2, RoundingMode.HALF_UP);
    }

    private String resolveResistanceAdvice(ShortTermSignalResp response) {
        if (Objects.isNull(response.getShanghaiKeyResistance())) {
            return "上证关键阻力位暂未获取，先以指数和情绪同步确认。";
        }
        if (Objects.nonNull(response.getResistanceDistancePct())
                && response.getResistanceDistancePct().signum() <= 0) {
            return "上证已触及或越过关键阻力位，关注收盘能否站稳。";
        }
        return "突破并站稳关键阻力位后，再提高短线进攻性。";
    }

    private ShortTermStrategyResp buildStrategy(ShortTermSignalResp response) {
        String ashareState = Objects.nonNull(response.getAshareEmotion())
                ? response.getAshareEmotion().getLabel() : "中性";
        boolean externalPanic = Objects.nonNull(response.getExternalEmotion())
                && "恐慌".equals(response.getExternalEmotion().getLabel());
        boolean belowResistance = Objects.nonNull(response.getResistanceDistancePct())
                && response.getResistanceDistancePct().signum() > 0;
        if ("恐慌".equals(ashareState) || externalPanic) {
            return ShortTermStrategyResp.builder()
                    .state("防守")
                    .title("先控制回撤，等待情绪修复")
                    .suitableCandidate("只保留已确认且风险分低的候选，观察项暂不执行")
                    .action("等待指数重新站稳关键位、广度改善或外部风险偏好修复")
                    .triggerCondition("A股情绪回到中性以上，指数和候选同步止跌")
                    .invalidCondition("放量跌破突破位，或市场广度继续恶化")
                    .positionAdvice("不开弱势反抽新仓，新增仓位仅允许极小试错")
                    .build();
        }
        if ("贪婪".equals(ashareState) && !belowResistance) {
            return ShortTermStrategyResp.builder()
                    .state("进攻")
                    .title("放量确认后做强，不追冲高")
                    .suitableCandidate("优先已确认的放量回踩不破，观察主线联动")
                    .action("等待回踩价位附近承接，确认指数和板块没有同步转弱")
                    .triggerCondition("候选收盘站上突破位，量比≥1.20，且板块保持强势")
                    .invalidCondition("跌回突破位下方，或指数放量下跌、板块出现明显退潮")
                    .positionAdvice("总仓仍受市场立场约束，单票小于组合上限，不因贪婪情绪追高")
                    .build();
        }
        return ShortTermStrategyResp.builder()
                .state("均衡")
                .title("只做结构确认，不抢反弹")
                .suitableCandidate("优先已确认候选，观察项等待收盘和板块确认")
                .action("核对突破位、回踩承接、指数阻力和主线强度后再安排模拟计划")
                .triggerCondition("价格重新站稳突破位，量价和指数方向一致")
                .invalidCondition("收盘跌破突破位-0.3ATR14，或市场环境转为防守")
                .positionAdvice("总仓控制在中等区间，非主线和冲高回落不追")
                .build();
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
