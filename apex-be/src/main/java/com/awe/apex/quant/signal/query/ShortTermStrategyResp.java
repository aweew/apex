package com.awe.apex.quant.signal.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 短线策略剧本。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortTermStrategyResp {

    /** 当前市场状态。 */
    private String state;

    /** 剧本标题。 */
    private String title;

    /** 适用候选。 */
    private String suitableCandidate;

    /** 观察动作。 */
    private String action;

    /** 触发条件。 */
    private String triggerCondition;

    /** 失效条件。 */
    private String invalidCondition;

    /** 仓位限制。 */
    private String positionAdvice;
}
