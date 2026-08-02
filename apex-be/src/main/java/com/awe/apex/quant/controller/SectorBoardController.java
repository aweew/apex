package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.SectorBoardItem;
import com.awe.apex.quant.domain.dto.SectorBoardResp;
import com.awe.apex.quant.domain.dto.SectorConstituentResp;
import com.awe.apex.quant.domain.dto.SectorRefreshResp;
import com.awe.apex.quant.service.ISectorBoardService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 板块行情看板（行业/概念/题材）
 */
@RestController
@RequestMapping("/api/sector")
public class SectorBoardController {

    @Resource
    private ISectorBoardService sectorBoardService;

    /**
     * 板块榜单
     *
     * @param type      类型 INDUSTRY/CONCEPT/THEME
     * @param sortBy    排序 pctChg/pctChg3d/pctChg5d/netInflow
     * @param order     方向 asc/desc
     * @param limit     条数
     * @param tradeDate 交易日 yyyy-MM-dd，可空
     * @return 榜单
     */
    @GetMapping("/board")
    public Result<SectorBoardResp> board(
            @RequestParam(defaultValue = "INDUSTRY") String type,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(defaultValue = "100") Integer limit,
            @RequestParam(required = false) String tradeDate) {
        return Result.success(sectorBoardService.board(type, sortBy, order, limit, tradeDate));
    }

    /**
     * 成分股列表
     *
     * @param code      板块代码
     * @param type      类型
     * @param sortBy    排序
     * @param order     方向
     * @param tradeDate 交易日 yyyy-MM-dd，可空
     * @return 成分股
     */
    @GetMapping("/{code}/constituents")
    public Result<SectorConstituentResp> constituents(
            @PathVariable String code,
            @RequestParam(defaultValue = "INDUSTRY") String type,
            @RequestParam(defaultValue = "pctChg") String sortBy,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(required = false) String tradeDate) {
        return Result.success(sectorBoardService.constituents(code, type, sortBy, order, tradeDate));
    }

    /**
     * 刷新板块榜单
     *
     * @param types 类型逗号分隔
     * @return 结果
     */
    @PostMapping("/refresh")
    public Result<SectorRefreshResp> refresh(
            @RequestParam(required = false, defaultValue = "INDUSTRY,CONCEPT,THEME") String types) {
        return Result.success(sectorBoardService.refresh(types));
    }

    /**
     * 主线识别
     *
     * @param tradeDate 交易日
     * @param limit     条数
     * @return 主线列表
     */
    @GetMapping("/mainline")
    public Result<List<SectorBoardItem>> mainline(
            @RequestParam(required = false) String tradeDate,
            @RequestParam(defaultValue = "8") Integer limit) {
        return Result.success(sectorBoardService.mainline(tradeDate, limit));
    }

    /**
     * 刷新单个板块成分股
     *
     * @param code 板块代码
     * @param type 类型
     * @return 结果
     */
    @PostMapping("/{code}/constituents/refresh")
    public Result<SectorRefreshResp> refreshConstituents(
            @PathVariable String code,
            @RequestParam(defaultValue = "INDUSTRY") String type) {
        return Result.success(sectorBoardService.refreshConstituents(code, type));
    }
}
