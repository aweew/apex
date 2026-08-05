package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 今日消息面卡片
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsPulseCardResp {

    /**
     * 新闻 ID
     */
    private Long id;

    /**
     * 情感：利好/利空/中性
     */
    private String sentiment;

    /**
     * 重要度 1-5（规则估算）
     */
    private Integer stars;

    /**
     * 标题
     */
    private String title;

    /**
     * 摘要
     */
    private String summary;

    /**
     * 题材标签
     */
    private List<String> themes;

    /**
     * 发布时间
     */
    private LocalDateTime publishedAt;

    /**
     * 来源
     */
    private String source;

    /**
     * 原文链接
     */
    private String url;

    /**
     * 是否要闻
     */
    private Boolean yaowen;
}
