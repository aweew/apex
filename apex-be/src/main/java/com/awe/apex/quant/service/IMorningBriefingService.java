package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.MorningBriefingResp;

/**
 * 盘前晨报服务。
 */
public interface IMorningBriefingService {

    /**
     * 汇总隔夜美股和夜间新闻。
     *
     * @return 盘前晨报
     */
    MorningBriefingResp generate();

    /**
     * 读取最近一次盘前晨报，缓存未命中时生成。
     *
     * @return 最近一次盘前晨报
     */
    MorningBriefingResp latest();

    /**
     * 清除盘前晨报缓存。
     */
    void invalidateCache();
}
