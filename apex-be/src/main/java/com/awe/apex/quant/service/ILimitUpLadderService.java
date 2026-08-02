package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.LimitUpLadderResp;
import com.awe.apex.quant.domain.dto.LimitUpRefreshResp;

/**
 * 连板天梯 / 涨停复盘
 */
public interface ILimitUpLadderService {

    /**
     * 查询连板天梯
     *
     * @param tradeDate 交易日可空
     * @return 天梯
     */
    LimitUpLadderResp ladder(String tradeDate);

    /**
     * 刷新涨停池并返回天梯
     *
     * @param tradeDate 交易日可空
     * @return 结果
     */
    LimitUpRefreshResp refresh(String tradeDate);
}
