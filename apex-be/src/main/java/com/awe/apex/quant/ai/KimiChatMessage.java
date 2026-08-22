package com.awe.apex.quant.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kimi 对话消息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KimiChatMessage {

    /**
     * 消息角色，支持 system、user、assistant。
     */
    private String role;

    /**
     * 消息文本。
     */
    private String content;
}
