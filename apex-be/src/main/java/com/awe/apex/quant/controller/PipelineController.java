package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.PipelineRunReq;
import com.awe.apex.quant.domain.dto.PipelineRunResp;
import com.awe.apex.quant.service.IPipelineService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 一键研究流水线
 */
@RestController
@RequestMapping("/api/pipeline")
public class PipelineController {

    @Resource
    private IPipelineService pipelineService;

    /**
     * 运行流水线：行情 → 日线 → 股票池 → 信号（可选日终）
     */
    @PostMapping("/run")
    public Result<PipelineRunResp> run(@RequestBody(required = false) PipelineRunReq req) {
        return Result.success(pipelineService.run(req));
    }
}
