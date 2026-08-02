package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.DecisionAttributionResp;
import com.awe.apex.quant.domain.dto.DecisionHistoryItem;
import com.awe.apex.quant.domain.dto.DecisionPlaybookResp;
import com.awe.apex.quant.domain.dto.DecisionRunReq;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import com.awe.apex.quant.service.IDecisionPlaybookService;
import com.awe.apex.quant.service.IDecisionService;
import jakarta.annotation.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 智能决策（一键策略拍板）
 */
@RestController
@RequestMapping("/api/decision")
public class DecisionController {

    @Resource
    private IDecisionService decisionService;

    @Resource
    private IDecisionPlaybookService decisionPlaybookService;

    /**
     * 策略战法与交易规则手册
     *
     * @return 手册
     */
    @GetMapping("/playbook")
    public Result<DecisionPlaybookResp> playbook() {
        return Result.success(decisionPlaybookService.playbook());
    }

    /**
     * 一键生成今日决策
     *
     * @param req 请求
     * @return 决策
     */
    @PostMapping("/run")
    public Result<DecisionTodayResp> run(@RequestBody(required = false) DecisionRunReq req) {
        return Result.success(decisionService.run(req));
    }

    /**
     * 读取今日决策清单
     *
     * @param date      日期
     * @param groupName 分组
     * @return 决策
     */
    @GetMapping("/today")
    public Result<DecisionTodayResp> today(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String groupName) {
        return Result.success(decisionService.today(date, groupName));
    }

    /**
     * 决策历史与事后收益
     *
     * @param limit 天数
     * @return 历史
     */
    @GetMapping("/history")
    public Result<List<DecisionHistoryItem>> history(@RequestParam(defaultValue = "15") Integer limit) {
        return Result.success(decisionService.history(limit));
    }

    /**
     * 决策复盘归因
     *
     * @param days 回溯天数
     * @return 归因
     */
    @GetMapping("/attribution")
    public Result<DecisionAttributionResp> attribution(@RequestParam(defaultValue = "20") Integer days) {
        return Result.success(decisionService.attribution(days));
    }
}
