package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 决策个股关联的近期新闻
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionStockNewsResp {

    /**
     * 新闻来源
     */
    private String source;

    /**
     * 发布时间
     */
    private LocalDateTime publishedAt;

    /**
     * 原始标题
     */
    private String title;

    /**
     * 原文链接
     */
    private String url;
}
