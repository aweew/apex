package com.awe.apex.quant.domain.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 信号运行请求
 */
@Data
public class SignalRunReq {

    /**
     * 代码列表，空则用最新 universe
     */
    private List<String> codes;

    /**
     * 策略ID列表，空则跑全部
     */
    private List<String> strategyIds;

    /**
     * 卖出信号关注代码，空值则使用当前用户全部活跃组合持仓
     */
    private List<String> sellCodes;

    /**
     * 是否使用最新股票池
     */
    private Boolean useUniverse;

    /**
     * 行情截止日；空值保持原有最新行情行为
     */
    private LocalDate asOfDate;
}
