package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Apex AI 核心指标
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApexAiMetric {

    /**
     * 指标名称
     */
    private String label;

    /**
     * 展示值
     */
    private String value;

    /**
     * 原始数值
     */
    private BigDecimal numericValue;

    /**
     * 指标单位
     */
    private String unit;

    /**
     * 视觉语义，支持 UP、DOWN、NEUTRAL、WARNING
     */
    private String tone;

    /**
     * 指标口径说明
     */
    private String detail;
}
