package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 滚动样本外评估窗口
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RollingBacktestFoldResp {

    /**
     * 窗口序号
     */
    private Integer foldNo;

    /**
     * 样本内开始日
     */
    private LocalDate trainBeginDate;

    /**
     * 样本内结束日
     */
    private LocalDate trainEndDate;

    /**
     * 样本外开始日
     */
    private LocalDate testBeginDate;

    /**
     * 样本外结束日
     */
    private LocalDate testEndDate;

    /**
     * 样本内累计收益
     */
    private BigDecimal inSampleReturn;

    /**
     * 样本内夏普
     */
    private BigDecimal inSampleSharpe;

    /**
     * 样本内年化收益
     */
    private BigDecimal inSampleAnnualReturn;

    /**
     * 样本外累计收益
     */
    private BigDecimal outSampleReturn;

    /**
     * 样本外夏普
     */
    private BigDecimal outSampleSharpe;

    /**
     * 样本外年化收益
     */
    private BigDecimal outSampleAnnualReturn;

    /**
     * 样本外最大回撤
     */
    private BigDecimal outSampleMaxDrawdown;

    /**
     * 同期基准收益
     */
    private BigDecimal benchmarkReturn;

    /**
     * 样本外相对基准超额收益
     */
    private BigDecimal excessReturn;

    /**
     * 样本外相对样本内年化收益衰减
     */
    private BigDecimal annualReturnDecay;

    /**
     * 样本外成交笔数
     */
    private Integer tradeCount;

    /**
     * 样本外期末未平仓数量
     */
    private Integer endingPositionQuantity;

    /**
     * 样本外期末未平仓市值
     */
    private BigDecimal endingPositionMarketValue;
}
