package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.DecisionAttributionResp;
import com.awe.apex.quant.domain.dto.DecisionBuyAiResp;
import com.awe.apex.quant.domain.dto.DecisionHistoryItem;
import com.awe.apex.quant.domain.dto.DecisionRunReq;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;

import java.time.LocalDate;
import java.util.List;

/**
 * 智能决策（一键策略拍板）
 */
public interface IDecisionService {

    /**
     * 一键生成今日决策：刷新股票池 → 跑策略 → 共振/基本面/风控 → 落库
     *
     * @param req 请求
     * @return 今日决策
     */
    DecisionTodayResp run(DecisionRunReq req);

    /**
     * 读取某日决策清单
     *
     * @param date      日期，可空=今天
     * @param groupName 分组（仅写入 message，可空）
     * @return 今日决策
     */
    DecisionTodayResp today(LocalDate date, String groupName);

    /**
     * 决策历史 + 买入建议事后次日收益
     *
     * @param limit 天数
     * @return 历史
     */
    List<DecisionHistoryItem> history(Integer limit);

    /**
     * 决策复盘归因（按策略/共振/主线/立场）
     *
     * @param days 回溯天数
     * @return 归因
     */
    DecisionAttributionResp attribution(Integer days);

    /**
     * 建议买入清单的 AI 详细总结
     *
     * @param date      决策日，可空=今天
     * @param groupName 分组名（写入上下文，可空）
     * @param force     是否强制刷新（跳过缓存）
     * @return AI 总结
     */
    DecisionBuyAiResp buyAiSummary(LocalDate date, String groupName, Boolean force);
}
