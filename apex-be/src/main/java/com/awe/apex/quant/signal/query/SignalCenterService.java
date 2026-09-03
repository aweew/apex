package com.awe.apex.quant.signal.query;

import java.math.BigDecimal;
import java.util.List;

/**
 * 市场行为信号中心服务。
 */
public interface SignalCenterService {

    /**
     * 执行市场行为计算。
     *
     * @param request 计算请求
     * @return 批次状态
     */
    SignalCalculationRunResp calculate(SignalCalculationReq request);

    /**
     * 查询计算批次。
     *
     * @param runNo 批次号
     * @return 批次状态
     */
    SignalCalculationRunResp getCalculation(String runNo);

    /**
     * 查询信号中心概览。
     *
     * @param timeframe 周期
     * @return 概览
     */
    SignalOverviewResp overview(String timeframe);

    /**
     * 查询市场行为排行。
     *
     * @param timeframe 周期
     * @param direction 方向
     * @param lifecycleState 生命周期状态
     * @param minStrength 最低强度
     * @param size 数量
     * @return 排行列表
     */
    List<SignalRankingItemResp> rankings(String timeframe, String direction, String lifecycleState,
                                         BigDecimal minStrength, Integer size);

    /**
     * 查询个股当前市场行为快照。
     *
     * @param symbol 证券代码
     * @param timeframe 周期
     * @return 个股快照
     */
    SignalStockSnapshotResp stockSnapshot(String symbol, String timeframe);

    /**
     * 查询个股生命周期时间轴。
     *
     * @param symbol 证券代码
     * @param timeframe 周期
     * @param size 数量
     * @return 时间轴
     */
    List<SignalTimelineItemResp> timeline(String symbol, String timeframe, Integer size);

    /**
     * 查询启用信号定义。
     *
     * @return 信号定义
     */
    List<SignalDefinitionResp> definitions();
}
