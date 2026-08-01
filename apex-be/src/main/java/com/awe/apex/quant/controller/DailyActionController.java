package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.entity.DailyAction;
import com.awe.apex.quant.service.IDailyActionService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 日终清单接口
 */
@RestController
@RequestMapping("/api/daily")
public class DailyActionController {

    @Resource
    private IDailyActionService dailyActionService;

    /**
     * 生成清单
     */
    @PostMapping("/run")
    public Result<List<DailyAction>> run(@RequestParam(required = false) String date) {
        LocalDate actionDate = date == null || date.isBlank() ? LocalDate.now() : LocalDate.parse(date);
        return Result.success(dailyActionService.run(actionDate));
    }

    /**
     * 今日清单快捷查询
     */
    @GetMapping("/checklist")
    public Result<List<DailyAction>> checklist() {
        return Result.success(dailyActionService.listByDate(LocalDate.now()));
    }

    /**
     * 按日期查询清单
     */
    @GetMapping("/{date:\\d{4}-\\d{2}-\\d{2}}")
    public Result<List<DailyAction>> list(@PathVariable String date) {
        return Result.success(dailyActionService.listByDate(LocalDate.parse(date)));
    }
}
