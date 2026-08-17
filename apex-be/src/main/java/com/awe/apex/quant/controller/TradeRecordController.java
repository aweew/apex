package com.awe.apex.quant.controller;

import com.awe.apex.common.api.PageResponse;
import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.TradeRecordResp;
import com.awe.apex.quant.service.IPortfolioTradeRecordService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户交易记录。
 */
@RestController
@RequestMapping("/api/trades")
public class TradeRecordController {

    @Resource
    private IPortfolioTradeRecordService tradeRecordService;

    /**
     * 分页查询当前用户交易记录。
     *
     * @param portfolioId 组合ID
     * @param code        证券代码
     * @param side        交易方向
     * @param source      记录来源
     * @param page        页码
     * @param size        每页条数
     * @return 分页交易记录
     */
    @GetMapping
    public Result<PageResponse<TradeRecordResp>> page(@RequestParam(required = false) Long portfolioId,
                                                      @RequestParam(required = false) String code,
                                                      @RequestParam(required = false) String side,
                                                      @RequestParam(required = false) String source,
                                                      @RequestParam(defaultValue = "1") Integer page,
                                                      @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(tradeRecordService.page(portfolioId, code, side, source, page, size));
    }

    /**
     * 查询当前用户指定证券的 K 线交易标记。
     *
     * @param code 证券代码
     * @return K 线交易标记
     */
    @GetMapping("/markers")
    public Result<List<TradeRecordResp>> markers(@RequestParam String code) {
        return Result.success(tradeRecordService.listMarkers(code));
    }
}
