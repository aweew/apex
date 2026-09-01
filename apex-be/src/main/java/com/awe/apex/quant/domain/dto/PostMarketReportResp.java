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
 * 最新交易日盘后总结。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PostMarketReportResp {

    /** 报告生成日期。 */
    private LocalDate reportDate;

    /** 报告对应的最新完成交易日。 */
    private LocalDate tradeDate;

    /** 报告生成时间。 */
    private LocalDateTime generatedAt;

    /** 本次使用的数据截至时间。 */
    private LocalDateTime dataAsOf;

    /** 数据等级 GREEN/YELLOW/RED。 */
    private String dataLevel;

    /** 生成来源 AI/RULE。 */
    private String reportSource;

    /** 正文质量 FULL/DEGRADED。 */
    private String contentLevel;

    /** 面向用户的数据质量提示。 */
    @Builder.Default
    private List<String> qualityWarnings = new ArrayList<>();

    /** 收盘核心结论。 */
    private String coreView;

    /** 市场状态。 */
    private String marketStatus;

    /** 当前最需要关注的风险。 */
    private String maxRisk;

    /** 大盘收盘快照。 */
    private MarketBriefingResp marketSnapshot;

    /** 行业板块榜单。 */
    @Builder.Default
    private List<SectorBoardItem> industryBoards = new ArrayList<>();

    /** 概念板块榜单。 */
    @Builder.Default
    private List<SectorBoardItem> conceptBoards = new ArrayList<>();

    /** 当日规则识别主线。 */
    @Builder.Default
    private List<SectorBoardItem> mainlines = new ArrayList<>();

    /** 当日明星个股。 */
    @Builder.Default
    private List<PostMarketStarStockResp> starStocks = new ArrayList<>();

    /** 当日龙虎榜明细。 */
    @Builder.Default
    private List<DragonTigerItemResp> dragonTigerItems = new ArrayList<>();

    /** 当日龙虎榜活跃席位和知名游资证据。 */
    @Builder.Default
    private List<PostMarketActiveSeatResp> activeSeats = new ArrayList<>();

    /** 缺失或日期不一致的数据项。 */
    @Builder.Default
    private List<String> missingData = new ArrayList<>();

    /** 可复制阅读的完整正文。 */
    private String content;
}
