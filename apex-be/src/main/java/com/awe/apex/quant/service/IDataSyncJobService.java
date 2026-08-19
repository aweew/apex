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
     * 由系统调度启动共享同步任务
     *
     * @param req 请求
     * @return 任务状态
     */
    SyncJobResp startSystemTask(SyncStartReq req);

    /**
     * 拒绝旧版按用户启动入口，智能决策统一由共享任务生成。
     *
     * @param req 请求
     * @param userId 所属用户ID
     * @return 不返回，始终抛出业务异常
     */
    SyncJobResp startForUser(SyncStartReq req, Long userId);

    /**
     * 判断当前用户的智能决策任务是否正在运行。
     *
     * @return true=正在运行
     */
    boolean isCurrentUserDecisionRunning();

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
