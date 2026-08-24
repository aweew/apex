package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.MarketOpinionRadarResp;

/**
 * 可追溯市场观点服务。
 */
public interface IMarketOpinionService {

    /**
     * 读取首页市场观点雷达。
     *
     * @return 市场观点雷达
     */
    MarketOpinionRadarResp radar();

    /**
     * 同步公开机构研报与龙虎榜活跃席位。
     */
    void refresh();
}
