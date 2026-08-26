package com.awe.apex.quant.domain.bo;

import com.awe.apex.quant.domain.dto.DashboardCommandResp;
import com.awe.apex.quant.domain.dto.DashboardHomeResp;
import com.awe.apex.quant.domain.dto.MarketBreadthForecastResp;
import com.awe.apex.quant.domain.dto.MorningBriefingResp;
import com.awe.apex.quant.domain.dto.ObservePoolResp;
import com.awe.apex.quant.domain.dto.PortfolioSummaryResp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 每日盘前研报生成上下文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyPreMarketReportContextBO {

    /**
     * 目标交易日。
     */
    private LocalDate tradeDate;

    /**
     * 上下文生成时间。
     */
    private LocalDateTime generatedAt;

    /**
     * 基于已同步市场广度、涨跌停和消息面计算的情绪分。
     */
    private Integer sentimentScore;

    /**
     * A 股市场摘要。
     */
    private DashboardHomeResp.MarketBlock market;

    /**
     * 隔夜市场与消息面快照。
     */
    private MorningBriefingResp morningBriefing;

    /**
     * 盘前涨跌广度预测。
     */
    private MarketBreadthForecastResp breadthForecast;

    /**
     * 盘前总结与操作指引。
     */
    private DashboardCommandResp command;

    /**
     * 当前用户今日决策摘要。
     */
    private DashboardHomeResp.DecisionBlock decision;

    /**
     * 当前用户观察池候选。
     */
    @Builder.Default
    private List<ObservePoolResp> observePool = new ArrayList<>();

    /**
     * 当前用户拥有的全部活跃组合及持仓；管理员可覆盖全部组合。
     */
    @Builder.Default
    private List<PortfolioSummaryResp> portfolios = new ArrayList<>();

    /**
     * 明确的数据缺口。
     */
    @Builder.Default
    private List<String> missingData = new ArrayList<>();
}
