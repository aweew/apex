package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.JournalCreateReq;
import com.awe.apex.quant.domain.entity.JournalTrade;
import com.awe.apex.quant.service.IJournalService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 人工成交日记
 */
@RestController
@RequestMapping("/api/journal")
public class JournalController {

    @Resource
    private IJournalService journalService;

    /**
     * 录入
     */
    @PostMapping
    public Result<JournalTrade> create(@Valid @RequestBody JournalCreateReq req) {
        return Result.success(journalService.create(req));
    }

    /**
     * 最近记录
     */
    @GetMapping
    public Result<List<JournalTrade>> latest(@RequestParam(defaultValue = "50") Integer limit) {
        return Result.success(journalService.latest(limit));
    }

    /**
     * 从清单一键填入
     */
    @PostMapping("/from-action/{actionId}")
    public Result<JournalTrade> fromAction(@PathVariable Long actionId,
                                           @RequestParam BigDecimal price,
                                           @RequestParam Integer quantity) {
        return Result.success(journalService.fromAction(actionId, price, quantity));
    }
}
