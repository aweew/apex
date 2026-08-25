package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.MarketBreadthForecastResp;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.dto.MorningBriefingResp;

import java.time.LocalDate;

/**
 * 盘前涨跌比预测与收盘回测服务。
 */
public interface IMarketBreadthForecastService {

    /**
     * 获取当前交易日的看板预测；盘前数据已齐全时补建一次预测。
     *
     * @param morningBriefing 盘前晨报
     * @param marketBriefing 昨收市场简报
     * @return 预测与回测信息
     */
    MarketBreadthForecastResp loadForDashboard(MorningBriefingResp morningBriefing, MarketBriefingResp marketBriefing);

    /**
     * 交易日前生成并固化盘前预测。
     *
     * @return 生成结果说明，成功或已存在时为空字符串
     */
    String generateBeforeOpen();

    /**
     * 根据收盘后的完整市场广度结算预测回测。
     *
     * @param tradeDate 收盘交易日
     * @return 结算结果说明，成功或无需结算时为空字符串
     */
    String settleAfterClose(LocalDate tradeDate);
}
