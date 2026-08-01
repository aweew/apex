package com.awe.apex.quant.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 回测运行请求
 */
@Data
public class BacktestRunReq {

    /**
     * 证券代码
     */
    @NotBlank(message = "code 不能为空")
    private String code;

    /**
     * 策略ID
     */
    @NotBlank(message = "strategyId 不能为空")
    private String strategyId;

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
    private BigDecimal initCash;
}
