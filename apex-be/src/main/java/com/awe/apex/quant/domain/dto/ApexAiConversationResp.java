package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Apex AI 最近会话响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApexAiConversationResp {

    /**
     * 会话ID。
     */
    private Long conversationId;

    /**
     * 会话标题。
     */
    private String title;

    /**
     * 最近分析类型。
     */
    private String lastAnalysisType;

    /**
     * 会话消息。
     */
    @Builder.Default
    private List<ApexAiConversationMessageResp> messages = new ArrayList<>();
}
