package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.SyncJobResp;
import com.awe.apex.quant.domain.dto.SyncOverviewResp;
import com.awe.apex.quant.domain.dto.SyncStartReq;

import java.util.List;

/**
 * 统一数据同步任务服务
 */
public interface IDataSyncJobService {

    /**
     * 总览：任务定义 + 最近运行
     *
     * @return 总览
     */
    SyncOverviewResp overview();

    /**
     * 启动任务
     *
     * @param req 请求
     * @return 任务状态
     */
    SyncJobResp start(SyncStartReq req);

    /**
     * 查询任务
     *
     * @param jobId 任务 ID
     * @return 状态
     */
    SyncJobResp getJob(Long jobId);

    /**
     * 停止任务
     *
     * @param jobId 任务 ID
     * @return 状态
     */
    SyncJobResp stop(Long jobId);

    /**
     * 最近任务列表
     *
     * @param limit 条数
     * @return 列表
     */
    List<SyncJobResp> recentJobs(Integer limit);
}
