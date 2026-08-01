package com.awe.apex.quant.domain.dto;

import lombok.Data;

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
     * 是否使用最新股票池
     */
    private Boolean useUniverse;
}
