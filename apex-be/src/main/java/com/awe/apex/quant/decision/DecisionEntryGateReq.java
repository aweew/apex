package com.awe.apex.quant.decision;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 开仓资格门禁入参
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionEntryGateReq {

    /**
     * 市场核心数据是否可用
     */
    private boolean dataSufficient;

    /**
     * 当日上涨家数
     */
    private Integer breadthUp;

    /**
     * 是否命中当日主线
     */
    private boolean mainlineMatch;

    /**
     * 是否行业逆主线
     */
    private boolean offMainline;

    /** 是否进入科技成长线 */
    private boolean growthLane;

    /**
     * 热点来源数量
     */
    private int hotSourceCount;
}
