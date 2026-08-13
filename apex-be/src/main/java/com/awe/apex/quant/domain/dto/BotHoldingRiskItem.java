package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ClawBot 真实持仓风险项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotHoldingRiskItem {

    /**
     * 风险等级：WARN / CRITICAL。
     */
    private String level;

    /**
     * 风险类型：DATA / STOP / LOSS。
     */
    private String riskType;

    /**
     * 证券代码。
     */
    private String code;

    /**
     * 证券名称。
     */
    private String name;

    /**
     * 风险说明。
     */
    private String message;
}
