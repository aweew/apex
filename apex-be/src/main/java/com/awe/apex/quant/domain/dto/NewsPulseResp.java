package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 今日消息面总览
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsPulseResp {

    /**
     * 利好条数
     */
    private Integer bullCount;

    /**
     * 利空条数
     */
    private Integer bearCount;

    /**
     * 中性条数
     */
    private Integer neutralCount;

    /**
     * 综合立场 pill，如「涨稍多」
     */
    private String biasLabel;

    /**
     * 市场 briefing 立场（进攻/均衡/防守）
     */
    private String marketStance;

    /**
     * 赚钱效应提示
     */
    private String effectHint;

    /**
     * 执行摘要（LLM 或规则）
     */
    private String executiveSummary;

    /**
     * 摘要来源：llm / rule
     */
    private String summarySource;

    /**
     * 热点主题名（简）
     */
    private List<String> hotThemes;

    /**
     * 消息面卡片
     */
    private List<NewsPulseCardResp> cards;

    /**
     * 说明
     */
    private String message;

    /**
     * 摘要生成时间
     */
    private LocalDateTime summarizedAt;

    /**
     * 模型是否已配置
     */
    private Boolean llmConfigured;
}
