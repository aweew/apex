package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 周末消息面证据项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeekendNewsItemResp {

    /** 新闻主键。 */
    private Long id;

    /** 来源。 */
    private String source;

    /** 来源内去重键。 */
    private String externalId;

    /** 标题。 */
    private String title;

    /** 摘要。 */
    private String summary;

    /** 发布时间。 */
    private LocalDateTime publishedAt;

    /** 情感方向。 */
    private String sentiment;

    /** 关联证券代码。 */
    @Builder.Default
    private List<String> relatedCodes = new ArrayList<>();

    /** 原文链接。 */
    private String url;

    /** 规则计算的重要度，范围 1-3。 */
    private Integer importance;
}
