package com.awe.apex.quant.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ClawBot 股票问答请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotAskReq {

    /**
     * 调用方请求号，可空。
     */
    @Size(max = 80)
    private String requestId;

    /**
     * 微信用户标识，可空。
     */
    @Size(max = 120)
    private String userId;

    /**
     * 会话标识，可空。
     */
    @Size(max = 120)
    private String conversationId;

    /**
     * 用户问题。
     */
    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题不能超过500字")
    private String question;
}
