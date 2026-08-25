package com.awe.apex.quant.holding;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 盘前事件类型。
 */
@Getter
@AllArgsConstructor
public enum PreMarketEventTypeEnum {

    POLICY("POLICY", "政策监管"),
    EMERGENCY("EMERGENCY", "突发事件"),
    EARNINGS("EARNINGS", "业绩"),
    ANNOUNCEMENT("ANNOUNCEMENT", "公司公告");

    /**
     * 类型编码。
     */
    private final String code;

    /**
     * 类型名称。
     */
    private final String desc;
}
