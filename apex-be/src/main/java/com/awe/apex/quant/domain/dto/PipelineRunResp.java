package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 一键研究流水线响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineRunResp {

    /**
     * 分组
     */
    private String groupName;

    /**
     * 行情刷新成功数
     */
    private Integer quoteSuccess;

    /**
     * 日线同步成功数
     */
    private Integer barSuccess;

    /**
     * 日线同步失败数
     */
    private Integer barFail;

    /**
     * 股票池入选数
     */
    private Integer universeCount;

    /**
     * 股票池批次号
     */
    private String universeBatchNo;

    /**
     * 信号条数
     */
    private Integer signalCount;

    /**
     * 日终清单条数
     */
    private Integer dailyCount;

    /**
     * 智能决策买入数
     */
    private Integer decisionBuyCount;

    /**
     * 智能决策卖出数
     */
    private Integer decisionSellCount;

    /**
     * 智能决策持有数
     */
    private Integer decisionHoldCount;

    /**
     * 可执行提示条数
     */
    private Integer decisionExecutableCount;

    /**
     * 低估/偏低条数
     */
    private Integer decisionValuationCheapCount;

    /**
     * 观察池写入数
     */
    private Integer observeUpserted;

    /**
     * 步骤摘要
     */
    private List<String> steps;
}
