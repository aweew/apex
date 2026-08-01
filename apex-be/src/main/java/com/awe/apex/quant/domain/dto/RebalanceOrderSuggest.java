package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 再平衡建议单
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RebalanceOrderSuggest {

    /**
     * 代码
     */
    private String code;

    /**
     * BUY/SELL
     */
    private String side;

    /**
     * 建议数量（整百）
     */
    private Integer quantity;

    /**
     * 参考价
     */
    private BigDecimal price;

    /**
     * 当前权重
     */
    private BigDecimal currentWeight;

    /**
     * 目标权重
     */
    private BigDecimal targetWeight;

    /**
     * 说明
     */
    private String reason;
}
