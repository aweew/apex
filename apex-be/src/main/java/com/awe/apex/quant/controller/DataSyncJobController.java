package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.SyncJobResp;
import com.awe.apex.quant.domain.dto.SyncOverviewResp;
import com.awe.apex.quant.domain.dto.SyncStartReq;
import com.awe.apex.quant.service.IDataSyncJobService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 统一数据同步（启停 / 进度）
 */
@RestController
@RequestMapping("/api/sync")
public class DataSyncJobController {

    @Resource
    private IDataSyncJobService dataSyncJobService;

    /**
     * 同步总览
     *
     * @return 任务清单与最近运行
     */
    @GetMapping("/overview")
    public Result<SyncOverviewResp> overview() {
        return Result.success(dataSyncJobService.overview());
    }

    /**
     * 启动同步
     *
     * @param req 请求
     * @return 任务
     */
    @PostMapping("/jobs")
    public Result<SyncJobResp> start(@RequestBody SyncStartReq req) {
        return Result.success(dataSyncJobService.start(req));
    }

    /**
     * 查询任务
     *
     * @param id 任务 ID
     * @return 状态
     */
    @GetMapping("/jobs/{id}")
    public Result<SyncJobResp> get(@PathVariable Long id) {
        return Result.success(dataSyncJobService.getJob(id));
    }

    /**
     * 停止任务
     *
     * @param id 任务 ID
     * @return 状态
     */
    @PostMapping("/jobs/{id}/stop")
    public Result<SyncJobResp> stop(@PathVariable Long id) {
        return Result.success(dataSyncJobService.stop(id));
    }

    /**
     * 最近任务
     *
     * @param limit 条数
     * @return 列表
     */
    @GetMapping("/jobs")
    public Result<List<SyncJobResp>> recent(@RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(dataSyncJobService.recentJobs(limit));
    }
}
