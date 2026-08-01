package com.awe.apex.quant.domain.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 组合回测请求（等权分仓）
 */
@Data
public class PortfolioBacktestReq {

    /**
     * 代码列表；空则取最新股票池
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
     * 组合初始资金
     */
    private BigDecimal initCash;

    /**
     * 最多标的数
     */
    private Integer limit;
}
