package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 赚钱效应观测（通达信风格六指标口径）
 * <p>
 * 展示：平均股价(800005) / 中位数(880009口径) / 全A等权(800010) / 微盘(800007) /
 * 中证1000(000852) / 沪深300(000300)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketEffectResp {

    /**
     * 平均股价指数点位（东财 800005）
     */
    private BigDecimal avgStockPrice;

    /**
     * 平均股价涨跌幅%（800005，散户账户体感）
     */
    private BigDecimal avgPctChg;

    /**
     * 全A涨幅中位数%（880009 口径：截面中位数）
     */
    private BigDecimal medianPctChg;

    /**
     * 全A等权涨跌幅%（东财 800010 优先，缺失时使用全A截面算术平均）
     */
    private BigDecimal equalWeightPctChg;

    /**
     * 微盘股涨跌幅%（东财 800007 Choice微盘，对齐 880823）
     */
    private BigDecimal microPctChg;

    /**
     * 截面样本数（中位数口径）
     */
    private Integer sampleSize;

    /**
     * 兼容旧字段：等同 microPctChg
     */
    private BigDecimal csi2000PctChg;

    /**
     * 微盘指数点位
     */
    private BigDecimal csi2000Close;

    /**
     * 中证1000涨跌幅%（000852）
     */
    private BigDecimal csi1000PctChg;

    /**
     * 沪深300涨跌幅%（000300）
     */
    private BigDecimal hs300PctChg;

    /**
     * 微盘相对大盘：micro − 沪深300（百分点）
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
     * 数据来源说明
     */
    private String source;
}
