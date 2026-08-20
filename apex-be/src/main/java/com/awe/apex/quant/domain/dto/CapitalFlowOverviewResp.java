package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 资金面与龙虎榜总览响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CapitalFlowOverviewResp {

    /** 最新北向资金 */
    private NorthboundFlowResp northboundFlow;

    /** 个股资金流交易日 */
    private LocalDate stockTradeDate;

    /** 个股资金流同步时间 */
    private LocalDateTime stockSyncedAt;

    /** 个股资金流榜单 */
    private List<StockFundFlowItem> stockFlows;

    /** 行业资金流榜单 */
    private SectorBoardResp industryFlows;

    /** 概念资金流榜单 */
    private SectorBoardResp conceptFlows;

    /** 龙虎榜交易日 */
    private LocalDate dragonTigerTradeDate;

    /** 龙虎榜同步时间 */
    private LocalDateTime dragonTigerSyncedAt;

    /** 龙虎榜明细 */
    private List<DragonTigerItemResp> dragonTigerItems;
}
