package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 盘前总结与今日操作指引。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardCommandResp {

    /**
     * 操作计划对应的目标交易日。
     */
    private LocalDate tradeDate;

    /**
     * A 股市场数据截至日。
     */
    private LocalDate marketDataAsOf;

    /**
     * 最近一次成功取得实时行情的时间。
     */
    private LocalDateTime marketDataUpdatedAt;

    /**
     * 个股决策实际使用的市场数据截至日。
     */
    private LocalDate decisionDataAsOf;

    /**
     * 指挥结果生成时间。
     */
    private LocalDateTime generatedAt;

    /**
     * 当前交易阶段。
     */
    private String phase;

    /**
     * 指挥结果可信状态。
     */
    private String status;

    /**
     * 市场数据等级。
     */
    private String dataLevel;

    /**
     * 盘前市场总结。
     */
    private PreMarketSummaryResp preMarketSummary;

    /**
     * 今日操作指引。
     */
    private TodayOperationGuideResp operationGuide;
}
