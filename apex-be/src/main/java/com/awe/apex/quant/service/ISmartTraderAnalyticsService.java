package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.TraderPortfolioResp;
import com.awe.apex.quant.domain.dto.TraderPositionResp;
import com.awe.apex.quant.domain.dto.TraderRankingResp;
import com.awe.apex.quant.domain.entity.SmartMoneyFactor;
import com.awe.apex.quant.domain.entity.TraderProfile;
import com.awe.apex.quant.domain.dto.SmartMoneyFactorStatsResp;

import java.time.LocalDate;
import java.util.List;

/** Smart Trader 账本、排名、画像与因子计算服务。 */
public interface ISmartTraderAnalyticsService {
    /**
     * 从已确认交易重建交易者持仓与每日资产快照。
     *
     * @param tradeDate 快照日期
     */
    void rebuild(LocalDate tradeDate);

    /**
     * 查询交易者当前持仓。
     *
     * @param traderId 交易者ID
     * @return 持仓列表
     */
    List<TraderPositionResp> positions(Long traderId);

    /**
     * 查询交易者账户快照。
     *
     * @param traderId 交易者ID
     * @return 账户快照
     */
    TraderPortfolioResp portfolio(Long traderId);

    /**
     * 查询指定类型排行榜。
     *
     * @param type TOTAL / DAILY / STEADY
     * @return 排行榜
     */
    List<TraderRankingResp> ranking(String type);

    /**
     * 查询交易者画像。
     *
     * @param traderId 交易者ID
     * @return 画像
     */
    TraderProfile profile(Long traderId);

    /**
     * 查询指定交易日 Smart Money 因子。
     *
     * @param tradeDate 交易日
     * @return 因子列表
     */
    List<SmartMoneyFactor> factors(LocalDate tradeDate);

    /**
     * 汇总 Smart Money 因子事后收益样本。
     *
     * @return 分持有期统计
     */
    List<SmartMoneyFactorStatsResp> factorStats();
}
