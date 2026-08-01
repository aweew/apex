package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.SignalConfluenceResp;
import com.awe.apex.quant.domain.dto.SignalForwardResp;
import com.awe.apex.quant.domain.dto.SignalRunReq;
import com.awe.apex.quant.domain.dto.SignalStatsResp;
import com.awe.apex.quant.domain.entity.StrategySignalEntity;
import com.awe.apex.quant.service.ISignalService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 策略信号接口
 */
@RestController
@RequestMapping("/api/signal")
public class SignalController {

    @Resource
    private ISignalService signalService;

    /**
     * 运行信号
     *
     * @param req 请求
     * @return 信号
     */
    @PostMapping("/run")
    public Result<List<StrategySignalEntity>> run(@RequestBody(required = false) SignalRunReq req) {
        if (req == null) {
            req = new SignalRunReq();
        }
        return Result.success(signalService.run(req));
    }

    /**
     * 最近信号
     *
     * @param limit        条数
     * @param dedupeByCode 按代码去重（默认 true）
     * @param minScore     最低评分，可空
     * @param side         BUY/SELL，可空
     * @return 列表
     */
    @GetMapping("/latest")
    public Result<List<StrategySignalEntity>> latest(@RequestParam(defaultValue = "50") Integer limit,
                                                     @RequestParam(defaultValue = "true") Boolean dedupeByCode,
                                                     @RequestParam(required = false) BigDecimal minScore,
                                                     @RequestParam(required = false) String side) {
        int size = Objects.isNull(limit) ? 50 : Math.max(1, Math.min(limit, 200));
        int fetch = Objects.nonNull(minScore) || StringUtils.isNotBlank(side) ? Math.min(200, size * 3) : size;
        List<StrategySignalEntity> raw = signalService.latest(fetch, Boolean.TRUE.equals(dedupeByCode));
        List<StrategySignalEntity> filtered = new ArrayList<>();
        for (StrategySignalEntity item : raw) {
            if (StringUtils.isNotBlank(side) && !side.equalsIgnoreCase(item.getSide())) {
                continue;
            }
            if (Objects.nonNull(minScore)
                    && (Objects.isNull(item.getScore()) || item.getScore().compareTo(minScore) < 0)) {
                continue;
            }
            filtered.add(item);
            if (filtered.size() >= size) {
                break;
            }
        }
        return Result.success(filtered);
    }

    /**
     * 近 N 日信号统计
     */
    @GetMapping("/stats")
    public Result<SignalStatsResp> stats(@RequestParam(defaultValue = "5") Integer days) {
        return Result.success(signalService.stats(days));
    }

    /**
     * 信号前瞻收益评估
     */
    @GetMapping("/forward")
    public Result<SignalForwardResp> forward(@RequestParam(defaultValue = "60") Integer lookbackDays,
                                             @RequestParam(defaultValue = "5") Integer horizonDays) {
        return Result.success(signalService.forwardEval(lookbackDays, horizonDays));
    }

    /**
     * 多策略共振
     */
    @GetMapping("/confluence")
    public Result<SignalConfluenceResp> confluence(@RequestParam(defaultValue = "5") Integer days,
                                                   @RequestParam(defaultValue = "2") Integer minStrategies) {
        return Result.success(signalService.confluence(days, minStrategies));
    }
}
