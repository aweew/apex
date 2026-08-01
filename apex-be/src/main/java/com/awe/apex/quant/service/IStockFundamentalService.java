package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.StockFundamentalResp;

/**
 * 个股基本面查询
 */
public interface IStockFundamentalService {

    /**
     * 查询本地落库的基本面（摘要 / 指标 / 三大报表）
     *
     * @param code            证券代码
     * @param periodLimit     摘要与指标期数
     * @param reportPeriodLimit 报表展示期数
     * @return 基本面
     */
    StockFundamentalResp query(String code, Integer periodLimit, Integer reportPeriodLimit);
}
