package com.awe.apex.quant.holding;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 盘前事件影响范围。
 */
@Getter
@AllArgsConstructor
public enum PreMarketImpactScopeEnum {

    MARKET("MARKET", "大盘"),
    THEME("THEME", "题材"),
    STOCK("STOCK", "个股");

    /**
     * 范围编码。
     */
    private final String code;

    /**
     * 范围名称。
     */
    private final String desc;
}
