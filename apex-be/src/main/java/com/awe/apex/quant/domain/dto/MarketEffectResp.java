package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 赚钱效应观测（平均股价 / 涨幅中位数 / 微盘等）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketEffectResp {

    /**
     * 全A平均股价（元）
     */
    private BigDecimal avgStockPrice;

    /**
     * 全A涨幅中位数（%）
     */
    private BigDecimal medianPctChg;

    /**
     * 截面样本数
     */
    private Integer sampleSize;

    /**
     * 中证2000涨跌幅%
     */
    private BigDecimal csi2000PctChg;

    /**
     * 中证2000点位
     */
    private BigDecimal csi2000Close;

    /**
     * 沪深300涨跌幅%
     */
    private BigDecimal hs300PctChg;

    /**
     * 微盘相对大盘：中证2000 − 沪深300（百分点）
     */
    private BigDecimal microVsLargePct;

    /**
     * 涨幅超过5%家数
     */
    private Integer strongUpCount;

    /**
     * 跌幅超过5%家数
     */
    private Integer strongDownCount;

    /**
     * 一句话提示
     */
    private String hint;

    /**
     * 数据来源：eastmoney-clist / stock_basic
     */
    private String source;
}
