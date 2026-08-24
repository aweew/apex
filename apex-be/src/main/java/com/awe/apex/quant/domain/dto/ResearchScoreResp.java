package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 个股横截面研究评分。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchScoreResp {

    /** 状态 READY/INSUFFICIENT/MISSING */
    private String status;

    /** 研究评分 */
    private BigDecimal score;

    /** 评分标签 */
    private String label;

    /** 模型版本 */
    private String model;

    /** 评分置信度 */
    private String confidence;

    /** 可用权重覆盖率 */
    private BigDecimal coverage;

    /** 快照交易日 */
    private LocalDate asOf;

    /** 研究维度 */
    private List<ResearchScoreComponentResp> components;

    /** 数据说明 */
    private String reason;
}
