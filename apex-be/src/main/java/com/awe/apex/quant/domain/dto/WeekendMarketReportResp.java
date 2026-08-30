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
 * 全市场周末消息面专题研报。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class WeekendMarketReportResp {

    /** 报告日期。 */
    private LocalDate reportDate;

    /** 统计周起始交易日。 */
    private LocalDate weekStartDate;

    /** 统计周结束交易日。 */
    private LocalDate weekEndDate;

    /** 最后交易日。 */
    private LocalDate lastTradeDate;

    /** 生成时间。 */
    private LocalDateTime generatedAt;

    /** 数据截至时间。 */
    private LocalDateTime dataAsOf;

    /** 数据等级 GREEN/YELLOW/RED。 */
    private String dataLevel;

    /** 生成来源 AI/RULE。 */
    private String reportSource;

    /** 核心观点。 */
    private String coreView;

    /** 市场状态。 */
    private String marketStatus;

    /** 最大风险。 */
    private String maxRisk;

    /** 上周指数表现。 */
    @Builder.Default
    private List<WeekendIndexPerformanceResp> indexPerformance = new ArrayList<>();

    /** 周五收盘快照。 */
    private WeekendFridaySnapshotResp fridaySnapshot;

    /** 周末消息。 */
    @Builder.Default
    private List<WeekendNewsItemResp> weekendNews = new ArrayList<>();

    /** 机构、席位和大 V 观点。 */
    @Builder.Default
    private List<WeekendOpinionItemResp> marketOpinions = new ArrayList<>();

    /** 下周交易主线。 */
    @Builder.Default
    private List<WeekendTradingThemeResp> tradingThemes = new ArrayList<>();

    /** 强势、震荡、转弱市场剧本。 */
    @Builder.Default
    private List<WeekendMarketScenarioResp> scenarios = new ArrayList<>();

    /** 缺失或截至日不一致的数据。 */
    @Builder.Default
    private List<String> missingData = new ArrayList<>();

    /** 可复制阅读的完整正文。 */
    private String content;
}
