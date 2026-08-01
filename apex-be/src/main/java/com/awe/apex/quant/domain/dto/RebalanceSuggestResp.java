package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 再平衡建议
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RebalanceSuggestResp {

    /**
     * 目标成分
     */
    private List<String> targetCodes;

    /**
     * 目标单票权重
     */
    private BigDecimal targetWeight;

    /**
     * 建议买卖单（仅建议，不自动下单）
     */
    private List<RebalanceOrderSuggest> orders;

    /**
     * 说明
     */
    private String message;
}
