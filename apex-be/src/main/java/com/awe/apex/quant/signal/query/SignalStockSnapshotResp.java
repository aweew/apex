package com.awe.apex.quant.signal.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 个股市场行为快照响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalStockSnapshotResp {

    /** 证券代码 */
    private String symbol;

    /** 证券名称 */
    private String name;

    /** 周期 */
    private String timeframe;

    /** 市场阶段 */
    private String marketState;

    /** 主要方向行为 */
    private List<SignalRankingItemResp> activeSignals;

    /** 风险行为 */
    private List<SignalRankingItemResp> riskSignals;

    /** 数据截止时间 */
    private LocalDateTime dataAsOf;

    /** 新鲜度状态 */
    private String freshness;

    /** 使用提示 */
    private String usageHint;
}
