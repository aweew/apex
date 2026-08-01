package com.awe.apex.quant.strategy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 策略信号结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategySignalResult {

    /**
     * 策略ID
     */
    private String strategyId;

    /**
     * 证券代码
     */
    private String code;

    /**
     * 信号日
     */
    private LocalDate signalDate;

    /**
     * 方向
     */
    private SignalSide side;

    /**
     * 评分
     */
    private BigDecimal score;

    /**
     * 理由
     */
    private Map<String, Object> reason;
}
