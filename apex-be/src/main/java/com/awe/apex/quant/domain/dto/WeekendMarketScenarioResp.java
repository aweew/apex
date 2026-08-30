package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 下周市场情景。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeekendMarketScenarioResp {

    /** 情景名称：强势/震荡/转弱。 */
    private String scenario;

    /** 触发条件。 */
    private String trigger;

    /** 应对建议。 */
    private String action;
}
