package com.awe.apex.quant.decision;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.DecisionStrategyPerformance;
import com.awe.apex.quant.mapper.DecisionOutcomeMapper;
import com.awe.apex.quant.context.ApexUserContext;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于成熟归因样本的策略评分校准器
 */
@Component
public class DecisionPerformanceCalibrator {

    private static final int MIN_SAMPLE_COUNT = 20;
    private static final BigDecimal MAX_ADJUSTMENT = new BigDecimal("5");

    @Resource
    private DecisionOutcomeMapper decisionOutcomeMapper;

    @Resource
    private ApexUserContext userContext;

    /**
     * 加载策略评分校准值
     *
     * @return 策略ID到评分调整值
     */
    public Map<String, BigDecimal> loadAdjustments() {
        List<DecisionStrategyPerformance> performanceRows = decisionOutcomeMapper
                .selectStrategyPerformance(userContext.currentUserId());
        Map<String, BigDecimal> adjustments = new HashMap<>();
        if (CollUtil.isEmpty(performanceRows)) {
            return adjustments;
        }
        for (DecisionStrategyPerformance performanceRow : performanceRows) {
            String strategyId = performanceRow.getStrategyId();
            int sampleCount = performanceRow.getSampleCount();
            if (StringUtils.isBlank(strategyId) || sampleCount < MIN_SAMPLE_COUNT) {
                continue;
            }
            BigDecimal averageExcess = performanceRow.getAvgExcess5d();
            BigDecimal winRate = performanceRow.getWinRate5d();
            BigDecimal excessScore = averageExcess.multiply(new BigDecimal("100"));
            BigDecimal winRateScore = winRate.subtract(new BigDecimal("0.50"))
                    .multiply(new BigDecimal("10"));
            BigDecimal adjustment = excessScore.add(winRateScore)
                    .max(MAX_ADJUSTMENT.negate())
                    .min(MAX_ADJUSTMENT)
                    .setScale(2, RoundingMode.HALF_UP);
            adjustments.put(strategyId, adjustment);
        }
        return adjustments;
    }

}
