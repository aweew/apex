package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 可追溯市场观点项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketOpinionItemResp {

    /** 观点类型 INSTITUTION/ACTIVE_SEAT/KOL */
    private String opinionType;

    /** 来源主体 */
    private String subjectName;

    /** 原始来源 */
    private String source;

    /** 标题 */
    private String title;

    /** 摘要 */
    private String summary;

    /** 评级或行为方向 */
    private String direction;

    /** 关联证券代码 */
    private String relatedCode;

    /** 关联证券名称 */
    private String relatedName;

    /** 关联行业或主题 */
    private String topic;

    /** 公开净买卖额，元 */
    private BigDecimal netAmount;

    /** 发布时间 */
    private LocalDateTime publishedAt;

    /** 原文或 PDF 链接 */
    private String url;
}
