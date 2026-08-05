package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 综合研判 · AI 实时解读
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAnalysisAiResp {

    /**
     * 是否已配置大模型
     */
    private Boolean configured;

    /**
     * 是否来自缓存
     */
    private Boolean fromCache;

    /**
     * 模型名
     */
    private String model;

    /**
     * AI 立场（可与规则立场不同）
     */
    private String stance;

    /**
     * 解读正文（约 120–200 字）
     */
    private String brief;

    /**
     * 关注点 / 检查清单
     */
    private List<String> watchPoints;

    /**
     * 风险提示
     */
    private String riskNote;

    /**
     * 行情是否刚刷新
     */
    private Boolean quoteRefreshed;

    /**
     * 生成时间
     */
    private LocalDateTime generatedAt;

    /**
     * 免责声明
     */
    private String disclaimer;
}
