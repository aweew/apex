package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 观察池技术指标监控项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObserveTechSignal {

    /**
     * 指标编码
     */
    private String key;

    /**
     * 展示名
     */
    private String label;

    /**
     * 是否满足（相对买卖方向有利）
     */
    private Boolean hit;

    /**
     * 补充数值/说明
     */
    private String detail;
}
