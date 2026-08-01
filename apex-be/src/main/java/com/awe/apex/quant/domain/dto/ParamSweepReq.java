package com.awe.apex.quant.domain.dto;

import lombok.Data;

/**
 * 均线参数扫描请求
 */
@Data
public class ParamSweepReq {

    /**
     * 标的代码
     */
    private String code;

    /**
     * 开始日期
     */
    private String beginDate;

    /**
     * 结束日期
     */
    private String endDate;

    /**
     * 初始资金
     */
    private java.math.BigDecimal initCash;

    /**
     * 快线候选，逗号分隔，默认 5,10,20
     */
    private String fastPeriods;

    /**
     * 慢线候选，逗号分隔，默认 20,60,120
     */
    private String slowPeriods;
}
