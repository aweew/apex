package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.ApexAiAnalysisResp;
import com.awe.apex.quant.domain.dto.ApexAiAnalyzeReq;
import com.awe.apex.quant.domain.dto.ApexAiContextResp;
import com.awe.apex.quant.service.IApexAiAnalystService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Apex AI 小灵接口
 */
@RestController
@RequestMapping("/api/apex-ai")
public class ApexAiController {

    @Resource
    private IApexAiAnalystService apexAiAnalystService;

    /**
     * 查询小灵工作台上下文
     *
     * @return 当前用户可分析的组合、策略和推荐问题
     */
    @GetMapping("/context")
    public Result<ApexAiContextResp> context() {
        return Result.success(apexAiAnalystService.context());
    }

    /**
     * 让小灵读取 Apex 数据完成分析
     *
     * @param request 分析请求
     * @return 结构化分析结果
     */
    @PostMapping("/analyze")
    public Result<ApexAiAnalysisResp> analyze(@Valid @RequestBody ApexAiAnalyzeReq request) {
        return Result.success(apexAiAnalystService.analyze(request));
    }
}
