package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 选股策略运行响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenerStrategyRunResp {

    /** 本次运行的策略 */
    private ScreenerStrategyResp strategy;

    /** 截面粗筛命中数量 */
    private Integer snapshotMatchedCount;

    /** 历史条件命中数量 */
    private Integer historicalMatchedCount;

    /** 最终命中数量 */
    private Integer matchedCount;

    /** 最终命中股票 */
    private List<ScreenerStrategyMatchResp> matches;

    /** 数据状态 */
    private ScreenerStrategyDataStatusResp dataStatus;
}
