package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.ValuationBriefResp;
import com.awe.apex.quant.domain.dto.ValuationResp;
import com.awe.apex.quant.domain.dto.ValuationScreenItemResp;
import com.awe.apex.quant.service.IValuationService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 综合估值
 */
@RestController
@RequestMapping("/api/valuation")
public class ValuationController {

    @Resource
    private IValuationService valuationService;

    /**
     * 个股完整估值
     *
     * @param code 证券代码
     * @return 估值结论
     */
    @GetMapping("/{code}")
    public Result<ValuationResp> evaluate(@PathVariable String code) {
        return Result.success(valuationService.evaluate(code));
    }

    /**
     * 轻量估值摘要
     *
     * @param code 证券代码
     * @return 摘要
     */
    @GetMapping("/{code}/brief")
    public Result<ValuationBriefResp> brief(@PathVariable String code) {
        return Result.success(valuationService.brief(code));
    }

    /**
     * 批量轻量估值
     *
     * @param codes 逗号分隔代码
     * @return code -> 摘要
     */
    @GetMapping("/brief/batch")
    public Result<Map<String, ValuationBriefResp>> briefBatch(@RequestParam String codes) {
        List<String> list = Arrays.stream(codes.split("[,，\\s]+"))
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toList());
        return Result.success(valuationService.briefBatch(list));
    }

    /**
     * 估值筛选列表
     *
     * @param universe market / watchlist / observe
     * @param limit    条数
     * @param level    可选档位
     * @return 列表
     */
    @GetMapping("/screen")
    public Result<List<ValuationScreenItemResp>> screen(
            @RequestParam(defaultValue = "market") String universe,
            @RequestParam(defaultValue = "30") Integer limit,
            @RequestParam(required = false) String level) {
        return Result.success(valuationService.screen(universe, limit, level));
    }
}
