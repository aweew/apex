package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 市场简报因子项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketFactorItem {

    /**
     * 因子名
     */
    private String name;

    /**
     * 读值
     */
    private String value;

    /**
     * 信号：偏多/中性/偏空/提示
     */
    private String signal;

    /**
     * 说明
     */
    private String note;
}
