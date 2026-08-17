package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.BacktestExperimentDetailResp;
import com.awe.apex.quant.domain.dto.BacktestExperimentListResp;
import com.awe.apex.quant.domain.dto.RollingBacktestReq;
import com.awe.apex.quant.domain.dto.RollingBacktestResp;

import java.util.List;

/**
 * 回测实验历史服务
 */
public interface IBacktestExperimentService {

    /**
     * 保存实验快照
     *
     * @param request 实际请求
     * @param result  完整结果
     * @return 实验ID
     */
    Long save(RollingBacktestReq request, RollingBacktestResp result);

    /**
     * 查询最近实验
     *
     * @param limit 条数
     * @return 实验摘要
     */
    List<BacktestExperimentListResp> list(Integer limit);

    /**
     * 查询实验详情
     *
     * @param id 实验ID
     * @return 实验详情
     */
    BacktestExperimentDetailResp detail(Long id);

    /**
     * 删除实验
     *
     * @param id 实验ID
     */
    void remove(Long id);
}
