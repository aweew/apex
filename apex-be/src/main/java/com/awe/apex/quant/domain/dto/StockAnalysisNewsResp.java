package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 个股综合研判关联的本地新闻事实
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAnalysisNewsResp {

    /**
     * 新闻来源
     */
    private String source;

    /**
     * 发布时间
     */
    private LocalDateTime publishedAt;

    /**
     * 原始新闻标题
     */
    private String title;

    /**
     * 原文链接
     */
    private String url;

    /**
     * 匹配方式：代码关联或名称命中
     */
    private String matchType;
}
