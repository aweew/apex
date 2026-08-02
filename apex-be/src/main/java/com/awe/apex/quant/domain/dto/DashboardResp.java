package com.awe.apex.quant.domain.dto;

import com.awe.apex.quant.domain.entity.DailyAction;
import com.awe.apex.quant.domain.entity.PaperPosition;
import com.awe.apex.quant.domain.entity.StrategySignalEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 仪表盘响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResp {

    /**
     * 风控概览
     */
    private RiskOverviewResp risk;

    /**
     * 持仓
     */
    private List<PaperPosition> positions;

    /**
     * 近五日信号
     */
    private List<StrategySignalEntity> recentSignals;

    /**
     * 今日清单
     */
    private List<DailyAction> todayActions;

    /**
     * 模拟盘绩效
     */
    private PaperMetricsResp paperMetrics;

    /**
     * 纸面权益曲线（按成交日回放）
     */
    private List<EquityPointResp> equityCurve;

    /**
     * 行业浮盈亏归因
     */
    private List<IndustryPnlResp> industryPnls;

    /**
     * 市场简报摘要
     */
    private MarketBriefingResp marketBriefing;

    /**
     * 当日涨停家数
     */
    private Integer limitUpCount;
}
