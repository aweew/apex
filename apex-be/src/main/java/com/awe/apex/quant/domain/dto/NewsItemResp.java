package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 新闻条目
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsItemResp {

    /**
     * 主键
     */
    private Long id;

    /**
     * 来源
     */
    private String source;

    /**
     * 标题
     */
    private String title;

    /**
     * 摘要
     */
    private String summary;

    /**
     * 正文
     */
    private String content;

    /**
     * 链接
     */
    private String url;

    /**
     * 发布时间
     */
    private LocalDateTime publishedAt;

    /**
     * 关联代码
     */
    private List<String> relatedCodes;

    /**
     * 情感
     */
    private String sentiment;
}
