package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.TradeEventIngestReq;
import com.awe.apex.quant.domain.dto.TradeEventIngestResp;
import com.awe.apex.quant.service.ITradeEventService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 交易事件接入。
 */
@RestController
@RequestMapping("/api/trade-events")
public class TradeEventController {

    @Resource
    private ITradeEventService tradeEventService;

    /**
     * 接收 OpenClaw 标准化后的交易事件。
     *
     * @param request 交易事件
     * @return 入库结果
     */
    @PostMapping("/ingest")
    public Result<TradeEventIngestResp> ingest(@RequestBody TradeEventIngestReq request) {
        return Result.success(tradeEventService.ingest(request));
    }

    /**
     * 确认待确认交易事件并生成正式交易流水。
     *
     * @param id 交易事件ID
     * @return 处理结果
     */
    @PostMapping("/{id}/confirm")
    public Result<TradeEventIngestResp> confirm(@PathVariable Long id) {
        return Result.success(tradeEventService.confirm(id));
    }

    /**
     * 拒绝交易事件。
     *
     * @param id 交易事件ID
     * @return 处理结果
     */
    @PostMapping("/{id}/reject")
    public Result<TradeEventIngestResp> reject(@PathVariable Long id) {
        return Result.success(tradeEventService.reject(id));
    }
}
