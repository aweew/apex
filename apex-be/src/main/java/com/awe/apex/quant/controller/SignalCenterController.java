package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.signal.query.SignalCalculationReq;
import com.awe.apex.quant.signal.query.SignalCalculationRunResp;
import com.awe.apex.quant.signal.query.SignalCenterService;
import com.awe.apex.quant.signal.query.SignalDefinitionResp;
import com.awe.apex.quant.signal.query.SignalOverviewResp;
import com.awe.apex.quant.signal.query.SignalRankingItemResp;
import com.awe.apex.quant.signal.query.SignalStockSnapshotResp;
import com.awe.apex.quant.signal.query.SignalTimelineItemResp;
import com.awe.apex.quant.service.ApexUserAuthService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 市场行为信号中心接口。
 */
@RestController
@RequestMapping("/api/signal-center")
public class SignalCenterController {

    @Resource
    private SignalCenterService signalCenterService;

    @Resource
    private ApexUserAuthService userAuthService;

    /**
     * 查询信号中心概览。
     *
     * @param timeframe DAY或WEEK
     * @return 概览
     */
    @GetMapping("/overview")
    public Result<SignalOverviewResp> overview(@RequestParam(defaultValue = "DAY") String timeframe) {
        return Result.success(signalCenterService.overview(timeframe));
    }

    /**
     * 查询市场行为排行榜。
     *
     * @param timeframe DAY或WEEK
     * @param direction BULLISH BEARISH RISK
     * @param lifecycleState 生命周期状态
     * @param minStrength 最低强度
     * @param size 数量
     * @return 排行列表
     */
    @GetMapping("/rankings")
    public Result<List<SignalRankingItemResp>> rankings(
            @RequestParam(defaultValue = "DAY") String timeframe,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String lifecycleState,
            @RequestParam(required = false) BigDecimal minStrength,
            @RequestParam(defaultValue = "50") Integer size) {
        return Result.success(signalCenterService.rankings(timeframe, direction, lifecycleState,
                minStrength, size));
    }

    /**
     * 查询启用信号定义。
     *
     * @return 信号定义
     */
    @GetMapping("/definitions")
    public Result<List<SignalDefinitionResp>> definitions() {
        return Result.success(signalCenterService.definitions());
    }

    /**
     * 查询个股当前市场行为快照。
     *
     * @param symbol 证券代码
     * @param timeframe DAY或WEEK
     * @return 个股快照
     */
    @GetMapping("/stocks/{symbol}")
    public Result<SignalStockSnapshotResp> stockSnapshot(@PathVariable String symbol,
                                                         @RequestParam(defaultValue = "DAY") String timeframe) {
        return Result.success(signalCenterService.stockSnapshot(symbol, timeframe));
    }

    /**
     * 查询个股信号生命周期时间轴。
     *
     * @param symbol 证券代码
     * @param timeframe DAY或WEEK
     * @param size 数量
     * @return 时间轴
     */
    @GetMapping("/stocks/{symbol}/timeline")
    public Result<List<SignalTimelineItemResp>> timeline(@PathVariable String symbol,
                                                         @RequestParam(defaultValue = "DAY") String timeframe,
                                                         @RequestParam(defaultValue = "50") Integer size) {
        return Result.success(signalCenterService.timeline(symbol, timeframe, size));
    }

    /**
     * 创建市场行为计算批次。
     *
     * @param request 计算请求
     * @return 批次状态
     */
    @PostMapping("/calculations")
    public Result<SignalCalculationRunResp> calculate(@Valid @RequestBody SignalCalculationReq request) {
        userAuthService.requireAdmin();
        return Result.success(signalCenterService.calculate(request));
    }

    /**
     * 查询市场行为计算批次。
     *
     * @param runNo 批次号
     * @return 批次状态
     */
    @GetMapping("/calculations/{runNo}")
    public Result<SignalCalculationRunResp> calculation(@PathVariable String runNo) {
        userAuthService.requireAdmin();
        return Result.success(signalCenterService.getCalculation(runNo));
    }
}
