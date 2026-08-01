package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.DecisionRunReq;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;

import java.time.LocalDate;

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
}
