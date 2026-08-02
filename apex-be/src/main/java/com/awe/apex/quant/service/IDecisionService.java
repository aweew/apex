package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.DecisionAttributionResp;
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
}
