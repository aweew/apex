package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.RiskOverviewResp;
import com.awe.apex.quant.domain.dto.RiskRuleUpdateReq;
import com.awe.apex.quant.domain.entity.RiskRule;
import com.awe.apex.quant.service.IPaperService;
import com.awe.apex.quant.service.IRiskService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 风控接口
 */
@RestController
@RequestMapping("/api/risk")
public class RiskController {

    @Resource
    private IRiskService riskService;

    @Resource
    private IPaperService paperService;

    /**
     * 风控概览
     */
    @GetMapping("/overview")
    public Result<RiskOverviewResp> overview(@RequestParam(required = false) Long accountId) {
        Long id = accountId;
        if (id == null) {
            id = paperService.defaultAccount().getId();
        }
        return Result.success(riskService.overview(id));
    }

    /**
     * 规则列表
     */
    @GetMapping("/rules")
    public Result<List<RiskRule>> rules() {
        return Result.success(riskService.listRules());
    }

    /**
     * 更新单条规则
     */
    @PutMapping("/rules")
    public Result<RiskRule> updateRule(@Valid @RequestBody RiskRuleUpdateReq req) {
        return Result.success(riskService.updateRule(req));
    }

    /**
     * 应用风控预设
     */
    @PostMapping("/rules/preset")
    public Result<List<RiskRule>> applyPreset(@RequestParam String preset) {
        return Result.success(riskService.applyPreset(preset));
    }
}
