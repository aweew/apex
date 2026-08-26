package com.awe.apex.quant.bot.service;

import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import com.awe.apex.quant.domain.dto.DailyPreMarketReportResp;
import com.awe.apex.quant.domain.dto.MorningBriefingResp;
import com.awe.apex.quant.domain.dto.BotHoldingRiskResp;
import com.awe.apex.quant.domain.dto.ObservePoolResp;
import com.awe.apex.quant.domain.dto.WatchlistMoverResp;

import java.util.List;

/**
 * ClawBot 主动通知服务。
 */
public interface IBotNotificationService {

    /**
     * 推送智能决策完成消息。
     *
     * @param decision 决策结果
     */
    void notifyDecision(DecisionTodayResp decision);

    /**
     * 推送盘前晨报。
     *
     * @param briefing 盘前晨报
     */
    void notifyMorningBriefing(MorningBriefingResp briefing);

    /**
     * 推送完整的每日盘前研报。
     *
     * @param report 每日盘前研报
     */
    void notifyDailyPreMarketReport(DailyPreMarketReportResp report);

    /**
     * 推送盘中异动汇总。
     *
     * @param movers       自选异动
     * @param observeItems 观察池告警
     * @param holdingRisk  真实持仓风控告警
     */
    void notifyMarketAlerts(WatchlistMoverResp movers,
                            List<ObservePoolResp> observeItems,
                            BotHoldingRiskResp holdingRisk);
}
