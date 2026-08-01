package com.awe.apex.quant.domain.dto;

import lombok.Data;

/**
 * 一键研究流水线请求
 */
@Data
public class PipelineRunReq {

    /**
     * 自选分组
     */
    private String groupName;

    /**
     * 是否刷新行情快照
     */
    private Boolean refreshQuotes;

    /**
     * 是否同步缺失/过期日线
     */
    private Boolean syncStaleBars;

    /**
     * 是否刷新股票池
     */
    private Boolean refreshUniverse;

    /**
     * 是否运行信号
     */
    private Boolean runSignals;

    /**
     * 是否生成日终清单
     */
    private Boolean runDaily;
}
