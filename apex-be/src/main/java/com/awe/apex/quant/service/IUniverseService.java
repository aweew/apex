package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.UniverseRefreshReq;
import com.awe.apex.quant.domain.dto.UniverseRefreshResp;
import com.awe.apex.quant.domain.entity.UniverseSnapshot;

import java.time.LocalDate;
import java.util.List;

/**
 * 股票池服务
 */
public interface IUniverseService {

    /**
     * 发布共享股票池
     *
     * @param req 请求
     * @return 结果
     */
    UniverseRefreshResp refresh(UniverseRefreshReq req);

    /**
     * 最新批次列表
     *
     * @return 列表
     */
    List<UniverseSnapshot> latest();

    /**
     * 查询指定股票池批次
     *
     * @param batchNo 批次号
     * @return 列表
     */
    List<UniverseSnapshot> listByBatchNo(String batchNo);

    /**
     * 查询截止日期当时可用的最新批次
     *
     * @param asOfDate 截止日期
     * @return 列表
     */
    List<UniverseSnapshot> latestAsOf(LocalDate asOfDate);
}
