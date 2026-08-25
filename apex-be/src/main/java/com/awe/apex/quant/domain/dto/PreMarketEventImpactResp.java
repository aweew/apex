package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 盘前事件对 A 股的结构化影响。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreMarketEventImpactResp {

    /**
     * 事件类型编码。
     */
    private String eventType;

    /**
     * 事件类型名称。
     */
    private String eventTypeName;

    /**
     * 影响范围编码：MARKET/THEME/STOCK。
     */
    private String impactScope;

    /**
     * 影响范围名称。
     */
    private String impactScopeName;

    /**
     * 盘前影响方向：利好/利空/待验证。
     */
    private String direction;

    /**
     * 重要度，取值 1-5。
     */
    private Integer priority;

    /**
     * 事件标题。
     */
    private String title;

    /**
     * 事件摘要。
     */
    private String summary;

    /**
     * 关联证券代码。
     */
    private List<String> relatedCodes;

    /**
     * 关联题材。
     */
    private List<String> themes;

    /**
     * 面向新手的影响说明。
     */
    private String impactExplanation;

    /**
     * 资讯来源。
     */
    private String source;

    /**
     * 原文链接。
     */
    private String url;

    /**
     * 发布时间。
     */
    private LocalDateTime publishedAt;

    /**
     * 是否交易所、公司或监管部门等官方来源。
     */
    private boolean officialSource;

    /**
     * 核验状态。
     */
    private String verificationStatus;
}
