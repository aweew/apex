package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.TradeEventIngestReq;
import com.awe.apex.quant.domain.dto.TradeEventIngestResp;

/**
 * AI 交易事件服务。
 */
public interface ITradeEventService {
    /**
     * 接收并处理 AI 标准化交易事件。
     *
     * @param request 入库请求
     * @return 处理结果
     */
    TradeEventIngestResp ingest(TradeEventIngestReq request);

    /**
     * 确认待确认交易事件。
     *
     * @param id 交易事件ID
     * @return 处理结果
     */
    TradeEventIngestResp confirm(Long id);

    /**
     * 拒绝交易事件。
     *
     * @param id 交易事件ID
     * @return 处理结果
     */
    TradeEventIngestResp reject(Long id);
}
