package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.StockAnalysisResp;

/**
 * 个股综合研判
 */
public interface IStockAnalysisService {

    /**
     * 一页汇总：技术面 + 估值 + 资金情绪 + 策略结论
     *
     * @param code     证券代码
     * @param side     BUY/SELL，默认 BUY
     * @param barLimit K 线条数
     * @return 综合研判
     */
    StockAnalysisResp analyze(String code, String side, Integer barLimit);

    /**
     * 一页汇总，可附加 AI 实时解读
     *
     * @param code     证券代码
     * @param side     BUY/SELL
     * @param barLimit K 线条数
     * @param withAi   是否调用大模型
     * @param forceAi  是否忽略缓存强制重跑 AI
     * @return 综合研判
     */
    StockAnalysisResp analyze(String code, String side, Integer barLimit, boolean withAi, boolean forceAi);
}
