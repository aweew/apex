package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 研究评分维度。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchScoreComponentResp {

    /** 因子键 */
    private String key;

    /** 因子名称 */
    private String label;

    /** 模型权重百分比 */
    private BigDecimal weight;

    /** 原始值 */
    private BigDecimal rawValue;

    /** 原始值单位 */
    private String unit;

    /** 可比组内分位数 */
    private BigDecimal percentile;

    /** 相对中性分位的有符号贡献分 */
    private BigDecimal contribution;

    /** 数据是否可用 */
    private Boolean available;

    /** 数据截至日期 */
    private LocalDate asOf;

    /** 可比组名称 */
    private String universeName;

    /** 可比组样本数 */
    private Integer universeSize;

    /** 因子说明 */
    private String description;
}
