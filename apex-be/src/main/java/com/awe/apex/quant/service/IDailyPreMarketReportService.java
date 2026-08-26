package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.DailyPreMarketReportResp;

/**
 * Apex 每日盘前研报服务。
 */
public interface IDailyPreMarketReportService {

    /**
     * 读取当前用户的当日盘前研报。
     *
     * @param forceRefresh 是否强制重新生成
     * @return 每日盘前研报
     */
    DailyPreMarketReportResp latest(boolean forceRefresh);

    /**
     * 使用指定用户身份生成当日盘前研报。
     *
     * @param userId Apex 用户ID
     * @return 每日盘前研报
     */
    DailyPreMarketReportResp generateForUser(Long userId);
}
