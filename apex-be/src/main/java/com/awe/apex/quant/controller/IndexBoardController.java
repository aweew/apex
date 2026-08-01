package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.IndexBoardResp;
import com.awe.apex.quant.domain.dto.IndexRefreshResp;
import com.awe.apex.quant.domain.entity.IndexBar;
import com.awe.apex.quant.service.IIndexBoardService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 大盘指数看板
 */
@RestController
@RequestMapping("/api/index")
public class IndexBoardController {

    @Resource
    private IIndexBoardService indexBoardService;

    /**
     * 分市场大盘看板
     *
     * @param sparkDays 迷你走势天数
     * @return 看板
     */
    @GetMapping("/board")
    public Result<IndexBoardResp> board(@RequestParam(defaultValue = "30") Integer sparkDays) {
        return Result.success(indexBoardService.board(sparkDays));
    }

    /**
     * 指数历史日线
     *
     * @param code  内部代码
     * @param limit 条数
     * @return 日线
     */
    @GetMapping("/{code}/bars")
    public Result<List<IndexBar>> bars(@PathVariable String code,
                                       @RequestParam(defaultValue = "120") Integer limit) {
        return Result.success(indexBoardService.bars(code, limit));
    }

    /**
     * 刷新指数历史（调用 sync_index.py）
     *
     * @param start 起始 yyyyMMdd
     * @return 结果
     */
    @PostMapping("/refresh")
    public Result<IndexRefreshResp> refresh(@RequestParam(required = false, defaultValue = "20180101") String start) {
        return Result.success(indexBoardService.refresh(start));
    }
}
