package com.awe.apex.quant.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 组合交易方向。
 */
@Getter
@AllArgsConstructor
public enum PortfolioTradeSideEnum {

    BUY("BUY", "买入"),
    SELL("SELL", "卖出");

    /** 方向编码 */
    private final String code;

    /** 方向说明 */
    private final String desc;
}
