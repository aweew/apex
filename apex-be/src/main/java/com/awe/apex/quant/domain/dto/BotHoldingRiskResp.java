package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * ClawBot 真实持仓风险摘要。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotHoldingRiskResp {

    /**
     * 持仓数量。
     */
    private Integer holdingCount;

    /**
     * 有有效行情的持仓数量。
     */
    private Integer quotedCount;

    /**
     * 严重风险数量。
     */
    private Integer criticalCount;

    /**
     * 警告数量。
     */
    private Integer warnCount;

    /**
     * 全部有效报价共同覆盖到的时间。
     */
    private String dataAsOf;

    /**
     * 风险项。
     */
    @Builder.Default
    private List<BotHoldingRiskItem> alerts = new ArrayList<>();
}
