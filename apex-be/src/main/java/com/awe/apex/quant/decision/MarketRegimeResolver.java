package com.awe.apex.quant.decision;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.mapper.BarDailyMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 市场状态指标解析器
 */
@Component
public class MarketRegimeResolver {

    private static final String BENCHMARK_CODE = "000300";
    private static final int MAX_BAR_COUNT = 260;

    @Resource
    private BarDailyMapper barDailyMapper;

    @Resource
    private MarketRegimeEvaluator marketRegimeEvaluator;

    /**
     * 基于时点行情解析市场状态
     *
     * @param actionDate 决策日
     * @return 市场状态
     */
    public MarketRegimeResult resolve(LocalDate actionDate) {
        List<BarDaily> bars = barDailyMapper.selectRecentBars(BENCHMARK_CODE, actionDate, MAX_BAR_COUNT);
        BigDecimal breadthPct = barDailyMapper.selectMarketAboveMa20Ratio(actionDate);
        if (CollUtil.isEmpty(bars) || bars.size() < 65 || Objects.isNull(breadthPct)) {
            return marketRegimeEvaluator.evaluate(MarketRegimeInput.builder().dataComplete(false).build());
        }
        List<BarDaily> ascendingBars = new ArrayList<>(bars);
        ascendingBars.sort(Comparator.comparing(BarDaily::getTradeDate));
        if (hasInvalidClose(ascendingBars)) {
            return marketRegimeEvaluator.evaluate(MarketRegimeInput.builder().dataComplete(false).build());
        }

        BigDecimal latestClose = ascendingBars.get(ascendingBars.size() - 1).getClosePrice();
        BigDecimal ma20 = averageClose(ascendingBars, ascendingBars.size() - 20, ascendingBars.size());
        BigDecimal ma60 = averageClose(ascendingBars, ascendingBars.size() - 60, ascendingBars.size());
        BigDecimal previousMa20 = averageClose(ascendingBars,
                ascendingBars.size() - 25, ascendingBars.size() - 5);
        BigDecimal peak20 = latestClose;
        for (int index = ascendingBars.size() - 20; index < ascendingBars.size(); index++) {
            peak20 = peak20.max(ascendingBars.get(index).getClosePrice());
        }
        BigDecimal drawdown20 = peak20.signum() > 0
                ? peak20.subtract(latestClose).divide(peak20, 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal volatilityPercentile = volatilityPercentile(ascendingBars);

        return marketRegimeEvaluator.evaluate(MarketRegimeInput.builder()
                .indexAboveMa60(latestClose.compareTo(ma60) >= 0)
                .ma20AboveMa60(ma20.compareTo(ma60) >= 0)
                .ma20SlopeUp(ma20.compareTo(previousMa20) > 0)
                .breadthPct(breadthPct.max(BigDecimal.ZERO).min(BigDecimal.ONE))
                .drawdown20(drawdown20.max(BigDecimal.ZERO).min(BigDecimal.ONE))
                .volatilityPercentile(volatilityPercentile)
                .dataComplete(true)
                .build());
    }

    private boolean hasInvalidClose(List<BarDaily> bars) {
        for (BarDaily bar : bars) {
            if (Objects.isNull(bar) || Objects.isNull(bar.getClosePrice()) || bar.getClosePrice().signum() <= 0) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal averageClose(List<BarDaily> bars, int beginIndex, int endIndex) {
        BigDecimal total = BigDecimal.ZERO;
        for (int index = beginIndex; index < endIndex; index++) {
            total = total.add(bars.get(index).getClosePrice());
        }
        return total.divide(BigDecimal.valueOf(endIndex - beginIndex), 8, RoundingMode.HALF_UP);
    }

    private BigDecimal volatilityPercentile(List<BarDaily> bars) {
        List<Double> returns = new ArrayList<>();
        for (int index = 1; index < bars.size(); index++) {
            double previous = bars.get(index - 1).getClosePrice().doubleValue();
            double current = bars.get(index).getClosePrice().doubleValue();
            returns.add(current / previous - 1D);
        }
        if (returns.size() < 20) {
            return BigDecimal.ONE;
        }
        double currentVolatility = standardDeviation(returns, returns.size() - 20, returns.size());
        int windowCount = 0;
        int notGreaterCount = 0;
        for (int endIndex = 20; endIndex <= returns.size(); endIndex++) {
            double historicalVolatility = standardDeviation(returns, endIndex - 20, endIndex);
            windowCount++;
            if (historicalVolatility <= currentVolatility) {
                notGreaterCount++;
            }
        }
        return BigDecimal.valueOf((double) notGreaterCount / windowCount)
                .setScale(6, RoundingMode.HALF_UP);
    }

    private double standardDeviation(List<Double> values, int beginIndex, int endIndex) {
        double average = 0D;
        for (int index = beginIndex; index < endIndex; index++) {
            average += values.get(index);
        }
        average /= endIndex - beginIndex;
        double variance = 0D;
        for (int index = beginIndex; index < endIndex; index++) {
            double difference = values.get(index) - average;
            variance += difference * difference;
        }
        return Math.sqrt(variance / (endIndex - beginIndex));
    }
}
