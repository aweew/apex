package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 因子指标
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FactorItemResp {

    /**
     * 因子键
     */
    private String key;

    /**
     * 因子名称
     */
    private String label;

    /**
     * 数值
     */
    private BigDecimal value;

    /**
     * 文本值
     */
    private String displayValue;

    /**
     * 单位
     */
    private String unit;

    /**
     * 数据状态 AVAILABLE/MISSING
     */
    private String status;

    /**
     * 因子口径说明
     */
    private String description;

    /**
     * 数据截止日期
     */
    private LocalDate asOf;
}
