package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.WeekendMarketReportResp;

/**
 * 全市场周末消息面专题研报服务。
 */
public interface IWeekendMarketReportService {

    /**
     * 读取可见窗口内的最新周末研报；窗口外不返回报告。
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
     * 在周日 21:00 至周一 09:30 可见窗口内刷新资讯和公开观点后重新生成周末研报。
     *
     * @return 周末研报
     */
    WeekendMarketReportResp refresh();
}
