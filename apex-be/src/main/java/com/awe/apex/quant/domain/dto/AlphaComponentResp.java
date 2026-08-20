package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Alpha 评分组成项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlphaComponentResp {

    /**
     * 因子键
     */
    private String key;

    /**
     * 因子名称
     */
    private String label;

    /**
     * 权重百分比
     */
    private BigDecimal weight;

    /**
     * 原始值
     */
    private BigDecimal rawValue;

    /**
     * 标准分
     */
    private BigDecimal score;

    /**
     * 数据是否可用
     */
    private Boolean available;

    /**
     * 数据截止日期
     */
    private LocalDate asOf;

    /**
     * 评分口径说明
     */
    private String description;
}
