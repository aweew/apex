package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Apex AI 会话消息响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApexAiConversationMessageResp {

    /**
     * 消息ID。
     */
    private Long id;

    /**
     * 消息角色。
     */
    private String role;

    /**
     * 消息文本。
     */
    private String content;

    /**
     * 分析类型。
     */
    private String analysisType;

    /**
     * 组合ID。
     */
    private Long portfolioId;

    /**
     * 策略ID。
     */
    private String strategyId;

    /**
     * 请求编号。
     */
    private String requestId;

    /**
     * 结构化分析结果。
     */
    private ApexAiAnalysisResp analysis;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;
}
