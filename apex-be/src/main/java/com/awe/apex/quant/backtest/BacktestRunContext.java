package com.awe.apex.quant.backtest;

import com.awe.apex.quant.strategy.Strategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 回测任务冻结上下文
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestRunContext {

    /**
     * 已冻结参数的策略
     */
    private Strategy strategy;

    /**
     * 当前策略参数快照
     */
    private String strategyParameters;

    /**
     * 策略对比批次ID
     */
    private String comparisonBatchId;

    /**
     * 策略对比集合
     */
    private String comparisonStrategyIds;

    /**
     * 对比策略配置SHA-256指纹
     */
    private String comparisonConfigFingerprint;
}
