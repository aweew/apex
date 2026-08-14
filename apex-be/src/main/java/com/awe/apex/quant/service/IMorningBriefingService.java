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
}
