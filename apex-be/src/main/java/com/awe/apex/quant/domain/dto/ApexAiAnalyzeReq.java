package com.awe.apex.quant.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Apex AI 分析请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApexAiAnalyzeReq {

    /**
     * 用户问题
     */
    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题不能超过500字")
    private String question;

    /**
     * 分析类型，支持 AUTO、PORTFOLIO、STRATEGY、GENERAL
     */
    private String analysisType;

    /**
     * 指定组合ID
     */
    private Long portfolioId;

    /**
     * 指定策略ID
     */
    @Size(max = 40, message = "策略ID不能超过40字")
    private String strategyId;

    /**
     * 策略回溯决策日数量
     */
    private Integer days;
}
