package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 权益曲线质量
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EquityQualityResp {

    /**
     * 样本天数
     */
    private Integer sampleDays;

    /**
     * 路径效率（净涨幅/绝对路径长度，0~1）
     */
    private BigDecimal pathEfficiency;

    /**
     * 日收益一阶自相关
     */
    private BigDecimal returnAutocorr1;

    /**
     * 正收益日占比
     */
    private BigDecimal upDayRatio;

    /**
     * 说明
     */
    private String message;
}
