package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.WeekendMarketReportResp;

/**
 * 全市场周末消息面专题研报服务。
 */
public interface IWeekendMarketReportService {

    /**
     * 读取最新周末研报；缓存未命中时按最近完整交易周生成。
     *
     * @param forceRefresh 是否强制重新生成
     * @return 周末研报
     */
    WeekendMarketReportResp latest(boolean forceRefresh);

    /**
     * 生成最新周末研报，不触发外部资讯同步。
     *
     * @return 周末研报
     */
    WeekendMarketReportResp generate();

    /**
     * 刷新资讯和公开观点后重新生成周末研报。
     *
     * @return 周末研报
     */
    WeekendMarketReportResp refresh();
}
