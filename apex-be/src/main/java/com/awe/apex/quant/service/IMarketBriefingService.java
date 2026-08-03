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

    /**
     * 生成市场简报
     *
     * @param forceRebuild true 时跳过快照秒回，同步完整重建立场/评分/主线
     * @return 简报
     */
    MarketBriefingResp briefing(boolean forceRebuild);

    /**
     * 清除简报内存缓存（指数同步后调用，避免看板仍展示冻住的快照点位）
     */
    void invalidateCache();
}
