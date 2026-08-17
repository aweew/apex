package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 滚动回测策略参数快照
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RollingStrategyConfig {

    /**
     * 策略ID
     */
    private String strategyId;

    /**
     * 策略逻辑版本
     */
    private String logicVersion;

    /**
     * S1快均线周期
     */
    private Integer s1FastMa;

    /**
     * S1慢均线周期
     */
    private Integer s1SlowMa;

    /**
     * S1成交量均线周期
     */
    private Integer s1VolumeMa;

    /**
     * S2趋势均线周期
     */
    private Integer s2Ma;

    /**
     * S2 RSI周期
     */
    private Integer s2RsiPeriod;

    /**
     * S2 RSI超卖阈值
     */
    private BigDecimal s2RsiOversold;

    /**
     * S2 RSI回升阈值
     */
    private BigDecimal s2RsiRebound;

    /**
     * S2 RSI超买阈值
     */
    private BigDecimal s2RsiOverbought;

    /**
     * S3突破回看周期
     */
    private Integer s3Lookback;

    /**
     * S3最小成交量比率
     */
    private BigDecimal s3VolumeRatio;
}
