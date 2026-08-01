package com.awe.apex.quant.domain.dto;

import com.awe.apex.quant.domain.entity.BacktestEquity;
import com.awe.apex.quant.domain.entity.BacktestJob;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 策略 vs 基准对比
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenchmarkCompareResp {

    /**
     * 策略回测任务
     */
    private BacktestJob job;

    /**
     * 标的代码
     */
    private String code;

    /**
     * 基准代码
     */
    private String benchmarkCode;

    /**
     * 策略累计收益
     */
    private BigDecimal strategyReturn;

    /**
     * 标的买入持有收益
     */
    private BigDecimal stockBuyHoldReturn;

    /**
     * 基准买入持有收益
     */
    private BigDecimal benchmarkReturn;

    /**
     * 相对基准超额收益
     */
    private BigDecimal excessReturn;

    /**
     * 策略权益曲线
     */
    private List<BacktestEquity> strategyEquities;

    /**
     * 基准买入持有归一权益（与策略同初始资金）
     */
    private List<EquityPointResp> benchmarkEquities;

    /**
     * 个股买入持有归一权益
     */
    private List<EquityPointResp> stockEquities;

    /**
     * 免责声明
     */
    private String disclaimer;
}
