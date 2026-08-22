package com.awe.apex.quant.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Apex AI 增强请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApexAiEnhanceReq {

    /**
     * 会话ID。
     */
    @NotNull(message = "会话ID不能为空")
    private Long conversationId;

    /**
     * 分析请求编号。
     */
    @NotBlank(message = "请求编号不能为空")
    @Size(max = 64, message = "请求编号不能超过64字")
    private String requestId;
}
