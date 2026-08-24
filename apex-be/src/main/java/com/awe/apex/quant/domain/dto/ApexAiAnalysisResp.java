package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Apex AI 分析响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApexAiAnalysisResp {

    /**
     * 会话ID。
     */
    private Long conversationId;

    /**
     * 请求编号
     */
    private String requestId;

    /**
     * 分析类型
     */
    private String analysisType;

    /**
     * 分析标题
     */
    private String title;

    /**
     * 分析结论
     */
    private String summary;

    /**
     * 组合ID
     */
    private Long portfolioId;

    /**
     * 策略ID
     */
    private String strategyId;

    /**
     * 归因总金额
     */
    private BigDecimal totalValue;

    /**
     * 未归因残差金额
     */
    private BigDecimal residualValue;

    /**
     * 数据完整度，支持 GREEN、YELLOW、RED
     */
    private String dataLevel;

    /**
     * 数据截至时间
     */
    private LocalDateTime dataAsOf;

    /**
     * 数据口径说明
     */
    private String dataNote;

    /**
     * 是否使用大模型增强
     */
    private Boolean aiEnhanced;

    /**
     * 分析生成时间
     */
    private LocalDateTime generatedAt;

    /**
     * 核心指标
     */
    @Builder.Default
    private List<ApexAiMetric> metrics = new ArrayList<>();

    /**
     * 归因与证据列表
     */
    @Builder.Default
    private List<ApexAiContributor> contributors = new ArrayList<>();

    /**
     * 调整建议
     */
    @Builder.Default
    private List<String> suggestions = new ArrayList<>();

    /**
     * 分析后的站内下一步动作。
     */
    @Builder.Default
    private List<ApexAiAction> actions = new ArrayList<>();

    /**
     * 推荐追问
     */
    @Builder.Default
    private List<String> followUpQuestions = new ArrayList<>();

    /**
     * 研究免责声明
     */
    private String disclaimer;
}
