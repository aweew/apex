package com.awe.apex.quant.decision;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 决策评分原始输入
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionFeatureInput {

    /** 策略信号分 */
    private BigDecimal signalScore;

    /** 是否被基本面硬门禁拦截 */
    private boolean fundExclude;

    /** 基本面是否偏弱 */
    private boolean fundWeak;

    /** 是否逆主线 */
    private boolean offMainline;

    /** 估值调分 */
    private Integer valuationScoreDelta;

    /** 市场仓位系数 */
    private BigDecimal buyWeightFactor;

    /** 单票仓位上限 */
    private BigDecimal singleLimit;

    /** 是否仅供观察 */
    private boolean observeOnly;

    /** 是否通过开仓门禁，非买入动作不适用 */
    private Boolean entryGatePassed;
}
