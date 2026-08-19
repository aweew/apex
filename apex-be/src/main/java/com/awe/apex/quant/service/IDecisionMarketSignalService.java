package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.bo.DecisionMarketScanReq;
import com.awe.apex.quant.domain.bo.DecisionMarketSnapshot;
import com.awe.apex.quant.domain.dto.SignalConfluenceResp;

import java.time.LocalDate;

/**
 * 共享市场决策信号服务。
 */
public interface IDecisionMarketSignalService {

    /**
     * 刷新指定股票池批次的共享买入扫描。
     *
     * @param req 扫描请求
     * @param progressListener 进度监听器
     * @return 完成的共享市场快照
     */
    DecisionMarketSnapshot refresh(DecisionMarketScanReq req, TaskProgressListener progressListener);

    /**
     * 读取精确匹配的已完成共享市场快照。
     *
     * @param actionDate 决策日期
     * @param universeBatchNo 股票池批次号
     * @param includeBj 是否包含北交所
     * @return 完成的共享市场快照
     */
    DecisionMarketSnapshot require(LocalDate actionDate, String universeBatchNo, boolean includeBj);

    /**
     * 计算共享买入信号共振。
     *
     * @param days 回看天数
     * @param minStrategies 最少策略数
     * @param asOfDate 截止日期
     * @param includeBj 是否包含北交所
     * @return 买入信号共振
     */
    SignalConfluenceResp confluence(int days, int minStrategies, LocalDate asOfDate, boolean includeBj);
}
