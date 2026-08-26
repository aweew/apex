package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Apex 每日盘前研报。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyPreMarketReportResp {

    /**
     * 研报对应的 A 股交易日。
     */
    private LocalDate tradeDate;

    /**
     * 研报生成时间。
     */
    private LocalDateTime generatedAt;

    /**
     * A 股市场数据截至日。
     */
    private LocalDate marketDataAsOf;

    /**
     * 市场状态。
     */
    private String marketStatus;

    /**
     * Apex 市场情绪分，范围 0-100，数据不足时为空。
     */
    private Integer sentimentScore;

    /**
     * 今日市场判断。
     */
    private String marketJudgement;

    /**
     * 数据等级 GREEN/YELLOW/RED。
     */
    private String dataLevel;

    /**
     * 生成来源 AI/RULE。
     */
    private String reportSource;

    /**
     * 本次纳入分析的组合数量。
     */
    private Integer portfolioCount;

    /**
     * 本次纳入分析的持仓数量。
     */
    private Integer holdingCount;

    /**
     * 缺失或不可核验的数据项。
     */
    @Builder.Default
    private List<String> missingData = new ArrayList<>();

    /**
     * 可直接阅读和分享的研报正文。
     */
    private String content;
}
