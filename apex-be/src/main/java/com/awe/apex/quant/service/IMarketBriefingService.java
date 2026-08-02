package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.MarketBriefingResp;

/**
 * 每日市场简报
 */
public interface IMarketBriefingService {

    /**
     * 生成市场简报（本地指数/板块/涨停）
     *
     * @return 简报
     */
    MarketBriefingResp briefing();
}
