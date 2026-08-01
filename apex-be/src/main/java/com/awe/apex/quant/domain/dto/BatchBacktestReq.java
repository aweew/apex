package com.awe.apex.quant.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量回测请求
 */
@Data
public class BatchBacktestReq {

    /**
     * 代码列表；空则用最新股票池
     */
    private List<String> codes;

    /**
     * 策略
     */
    private String strategyId;

    /**
     * 开始
     */
    private String beginDate;

    /**
     * 结束
     */
    private String endDate;

    /**
     * 最多回测只数
     */
    private Integer limit;
}
