package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 周末研报引用的机构、席位或大 V 观点。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeekendOpinionItemResp {

    /** 观点类型 INSTITUTION/ACTIVE_SEAT/KOL。 */
    private String opinionType;

    /** 观点主体。 */
    private String subjectName;

    /** 来源。 */
    private String source;

    /** 标题。 */
    private String title;

    /** 摘要。 */
    private String summary;

    /** 方向。 */
    private String direction;

    /** 关联证券代码。 */
    private String relatedCode;

    /** 关联证券名称。 */
    private String relatedName;

    /** 关联主题。 */
    private String topic;

    /** 净买卖额。 */
    private BigDecimal netAmount;

    /** 发布时间。 */
    private LocalDateTime publishedAt;

    /** 原文链接。 */
    private String url;
}
