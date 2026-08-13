package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.backtest.BacktestCostConfig;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.DailyAction;
import com.awe.apex.quant.domain.entity.DecisionOutcome;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.DecisionOutcomeMapper;
import com.awe.apex.quant.service.IConfigService;
import com.awe.apex.quant.service.IDecisionOutcomeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 智能决策结果归因服务实现
 */
@Slf4j
@Service
public class DecisionOutcomeServiceImpl implements IDecisionOutcomeService {

    private static final String BENCHMARK_CODE = "000300";
    private static final int MAX_HORIZON = 20;
    private static final MathContext CALCULATION_CONTEXT = new MathContext(16, RoundingMode.HALF_UP);

    @Resource
    private DecisionOutcomeMapper decisionOutcomeMapper;

    @Resource
    private BarDailyMapper barDailyMapper;

    @Resource
    private IConfigService configService;

    /**
     * 幂等补算尚未完整归因的操作清单
     *
     * @return 成功保存数量
     */
    @Override
    public int calculatePendingOutcomes() {
        List<DecisionOutcome> pendingOutcomes = decisionOutcomeMapper.selectPendingOutcomes();
        if (CollUtil.isEmpty(pendingOutcomes)) {
            return 0;
        }

        BacktestCostConfig costConfig = resolveCostConfig();
        int savedCount = 0;
        for (DecisionOutcome pendingOutcome : pendingOutcomes) {
            DailyAction action = DailyAction.builder()
                    .id(pendingOutcome.getActionId())
                    .runId(pendingOutcome.getRunId())
                    .code(pendingOutcome.getCode())
                    .actionDate(pendingOutcome.getActionDate())
                    .build();
            List<BarDaily> stockBars = barDailyMapper.selectOutcomeBars(
                    action.getCode(), action.getActionDate(), MAX_HORIZON);
            List<BarDaily> benchmarkBars = List.of();
            if (CollUtil.isNotEmpty(stockBars)) {
                LocalDate beginDate = stockBars.get(0).getTradeDate();
                LocalDate endDate = stockBars.get(stockBars.size() - 1).getTradeDate();
                benchmarkBars = barDailyMapper.selectOutcomeBarsBetween(BENCHMARK_CODE, beginDate, endDate);
            }

            DecisionOutcome outcome = calculateOutcome(action, stockBars, benchmarkBars, costConfig);
            outcome.setFeatureSnapshotId(pendingOutcome.getFeatureSnapshotId());
            if (decisionOutcomeMapper.upsert(outcome) <= 0) {
                throw new BusinessException("决策结果归因保存失败，featureSnapshotId="
                        + pendingOutcome.getFeatureSnapshotId());
            }
            savedCount++;
        }
        log.info("决策结果归因补算完成，待补算数量={}，保存数量={}", pendingOutcomes.size(), savedCount);
        return savedCount;
    }

    /**
     * 计算单条操作清单的多周期理论归因
     *
     * @param action        操作清单
     * @param stockBars     决策日后的个股日线
     * @param benchmarkBars 同期沪深300日线
     * @param costConfig    交易成本配置
     * @return 归因结果
     */
    @Override
    public DecisionOutcome calculateOutcome(DailyAction action, List<BarDaily> stockBars,
                                            List<BarDaily> benchmarkBars, BacktestCostConfig costConfig) {
        if (Objects.isNull(action)) {
            throw new BusinessException("操作清单不能为空");
        }
        LocalDateTime calculatedAt = LocalDateTime.now();
        DecisionOutcome outcome = DecisionOutcome.builder()
                .actionId(action.getId())
                .runId(action.getRunId())
                .code(action.getCode())
                .actionDate(action.getActionDate())
                .status("PENDING")
                .qualityStatus("MISSING_ENTRY_BAR")
                .calculatedAt(calculatedAt)
                .createTime(calculatedAt)
                .updateTime(calculatedAt)
                .deleted(0)
                .build();
        if (CollUtil.isEmpty(stockBars)) {
            return outcome;
        }

        BarDaily entryBar = stockBars.get(0);
        BigDecimal entryOpen = entryBar.getOpenPrice();
        outcome.setEntryDate(entryBar.getTradeDate());
        outcome.setEntryPrice(entryOpen);
        if (Objects.isNull(entryOpen) || entryOpen.signum() <= 0) {
            outcome.setStatus("INVALID");
            outcome.setQualityStatus("MISSING_ENTRY_OPEN");
            return outcome;
        }

        BacktestCostConfig effectiveCosts = Objects.nonNull(costConfig)
                ? costConfig : BacktestCostConfig.defaults();
        Map<LocalDate, BarDaily> benchmarkByDate = buildBenchmarkByDate(benchmarkBars);
        setHorizon(outcome, stockBars, benchmarkByDate, effectiveCosts, entryOpen, 1);
        setHorizon(outcome, stockBars, benchmarkByDate, effectiveCosts, entryOpen, 3);
        setHorizon(outcome, stockBars, benchmarkByDate, effectiveCosts, entryOpen, 5);
        setHorizon(outcome, stockBars, benchmarkByDate, effectiveCosts, entryOpen, 10);
        setHorizon(outcome, stockBars, benchmarkByDate, effectiveCosts, entryOpen, 20);
        calculateExcursions(outcome, stockBars, entryOpen);

        if (stockBars.size() < MAX_HORIZON) {
            outcome.setStatus("PARTIAL");
            outcome.setQualityStatus("PERIOD_NOT_MATURE");
            return outcome;
        }
        if (hasMissingStockClose(stockBars)) {
            outcome.setStatus("PARTIAL");
            outcome.setQualityStatus("MISSING_CLOSE");
            return outcome;
        }
        if (isBenchmarkIncomplete(stockBars, benchmarkByDate)) {
            outcome.setStatus("PARTIAL");
            outcome.setQualityStatus("MISSING_BENCHMARK");
            return outcome;
        }
        outcome.setStatus("COMPLETE");
        outcome.setQualityStatus("COMPLETE");
        return outcome;
    }

    private BacktestCostConfig resolveCostConfig() {
        return BacktestCostConfig.of(
                configService.getDecimal("commission_rate", new BigDecimal("0.0005")),
                configService.getDecimal("stamp_tax_rate", new BigDecimal("0.0005")),
                configService.getDecimal("buy_slippage", new BigDecimal("0.001")),
                configService.getDecimal("sell_slippage", new BigDecimal("0.001"))
        );
    }

    private Map<LocalDate, BarDaily> buildBenchmarkByDate(List<BarDaily> benchmarkBars) {
        Map<LocalDate, BarDaily> benchmarkByDate = new HashMap<>();
        if (CollUtil.isEmpty(benchmarkBars)) {
            return benchmarkByDate;
        }
        for (BarDaily benchmarkBar : benchmarkBars) {
            if (Objects.nonNull(benchmarkBar) && Objects.nonNull(benchmarkBar.getTradeDate())) {
                benchmarkByDate.put(benchmarkBar.getTradeDate(), benchmarkBar);
            }
        }
        return benchmarkByDate;
    }

    private void setHorizon(DecisionOutcome outcome, List<BarDaily> stockBars,
                            Map<LocalDate, BarDaily> benchmarkByDate, BacktestCostConfig costConfig,
                            BigDecimal entryOpen, int horizon) {
        if (stockBars.size() < horizon) {
            return;
        }
        BarDaily exitBar = stockBars.get(horizon - 1);
        BigDecimal stockReturn = calculateNetReturn(entryOpen, exitBar.getClosePrice(), costConfig);
        setReturn(outcome, horizon, stockReturn);
        if (horizon == MAX_HORIZON) {
            outcome.setNetReturn(stockReturn);
        }
        if (Objects.isNull(stockReturn)) {
            return;
        }

        BarDaily benchmarkEntryBar = benchmarkByDate.get(stockBars.get(0).getTradeDate());
        BarDaily benchmarkExitBar = benchmarkByDate.get(exitBar.getTradeDate());
        BigDecimal benchmarkReturn = calculateMarketReturn(benchmarkEntryBar, benchmarkExitBar);
        if (Objects.nonNull(benchmarkReturn)) {
            setExcess(outcome, horizon, stockReturn.subtract(benchmarkReturn).setScale(6, RoundingMode.HALF_UP));
        }
    }

    private BigDecimal calculateNetReturn(BigDecimal entryOpen, BigDecimal exitClose,
                                          BacktestCostConfig costConfig) {
        if (Objects.isNull(exitClose) || exitClose.signum() <= 0) {
            return null;
        }
        BigDecimal buyPrice = entryOpen.multiply(BigDecimal.ONE.add(costConfig.getBuySlippage()),
                CALCULATION_CONTEXT);
        BigDecimal buyCost = buyPrice.multiply(BigDecimal.ONE.add(costConfig.getCommissionRate()),
                CALCULATION_CONTEXT);
        BigDecimal sellPrice = exitClose.multiply(BigDecimal.ONE.subtract(costConfig.getSellSlippage()),
                CALCULATION_CONTEXT);
        BigDecimal sellNet = sellPrice.multiply(BigDecimal.ONE
                        .subtract(costConfig.getCommissionRate())
                        .subtract(costConfig.getStampTaxRate()),
                CALCULATION_CONTEXT);
        return sellNet.divide(buyCost, CALCULATION_CONTEXT)
                .subtract(BigDecimal.ONE)
                .setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateMarketReturn(BarDaily entryBar, BarDaily exitBar) {
        if (Objects.isNull(entryBar) || Objects.isNull(exitBar)
                || Objects.isNull(entryBar.getOpenPrice()) || entryBar.getOpenPrice().signum() <= 0
                || Objects.isNull(exitBar.getClosePrice()) || exitBar.getClosePrice().signum() <= 0) {
            return null;
        }
        return exitBar.getClosePrice().divide(entryBar.getOpenPrice(), CALCULATION_CONTEXT)
                .subtract(BigDecimal.ONE)
                .setScale(6, RoundingMode.HALF_UP);
    }

    private void calculateExcursions(DecisionOutcome outcome, List<BarDaily> stockBars, BigDecimal entryOpen) {
        BigDecimal highestPrice = null;
        BigDecimal lowestPrice = null;
        int barCount = Math.min(stockBars.size(), MAX_HORIZON);
        for (int index = 0; index < barCount; index++) {
            BarDaily bar = stockBars.get(index);
            if (Objects.nonNull(bar.getHighPrice())
                    && (Objects.isNull(highestPrice) || bar.getHighPrice().compareTo(highestPrice) > 0)) {
                highestPrice = bar.getHighPrice();
            }
            if (Objects.nonNull(bar.getLowPrice())
                    && (Objects.isNull(lowestPrice) || bar.getLowPrice().compareTo(lowestPrice) < 0)) {
                lowestPrice = bar.getLowPrice();
            }
        }
        if (Objects.nonNull(highestPrice)) {
            outcome.setMfe(highestPrice.divide(entryOpen, CALCULATION_CONTEXT)
                    .subtract(BigDecimal.ONE).setScale(6, RoundingMode.HALF_UP));
        }
        if (Objects.nonNull(lowestPrice)) {
            outcome.setMae(lowestPrice.divide(entryOpen, CALCULATION_CONTEXT)
                    .subtract(BigDecimal.ONE).setScale(6, RoundingMode.HALF_UP));
        }
    }

    private boolean hasMissingStockClose(List<BarDaily> stockBars) {
        for (int index = 0; index < MAX_HORIZON; index++) {
            BarDaily stockBar = stockBars.get(index);
            if (Objects.isNull(stockBar.getClosePrice()) || stockBar.getClosePrice().signum() <= 0) {
                return true;
            }
        }
        return false;
    }

    private boolean isBenchmarkIncomplete(List<BarDaily> stockBars, Map<LocalDate, BarDaily> benchmarkByDate) {
        BarDaily benchmarkEntryBar = benchmarkByDate.get(stockBars.get(0).getTradeDate());
        if (Objects.isNull(benchmarkEntryBar) || Objects.isNull(benchmarkEntryBar.getOpenPrice())
                || benchmarkEntryBar.getOpenPrice().signum() <= 0) {
            return true;
        }
        int[] horizonIndexes = {0, 2, 4, 9, 19};
        for (int horizonIndex : horizonIndexes) {
            BarDaily benchmarkBar = benchmarkByDate.get(stockBars.get(horizonIndex).getTradeDate());
            if (Objects.isNull(benchmarkBar) || Objects.isNull(benchmarkBar.getClosePrice())
                    || benchmarkBar.getClosePrice().signum() <= 0) {
                return true;
            }
        }
        return false;
    }

    private void setReturn(DecisionOutcome outcome, int horizon, BigDecimal value) {
        switch (horizon) {
            case 1 -> outcome.setReturn1d(value);
            case 3 -> outcome.setReturn3d(value);
            case 5 -> outcome.setReturn5d(value);
            case 10 -> outcome.setReturn10d(value);
            case 20 -> outcome.setReturn20d(value);
            default -> throw new BusinessException("不支持的归因周期: " + horizon);
        }
    }

    private void setExcess(DecisionOutcome outcome, int horizon, BigDecimal value) {
        switch (horizon) {
            case 1 -> outcome.setExcess1d(value);
            case 3 -> outcome.setExcess3d(value);
            case 5 -> outcome.setExcess5d(value);
            case 10 -> outcome.setExcess10d(value);
            case 20 -> outcome.setExcess20d(value);
            default -> throw new BusinessException("不支持的超额收益周期: " + horizon);
        }
    }
}
