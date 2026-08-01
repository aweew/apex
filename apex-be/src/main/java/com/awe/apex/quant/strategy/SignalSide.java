package com.awe.apex.quant.strategy;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 信号方向
 */
@Getter
@AllArgsConstructor
public enum SignalSide {

    BUY("BUY", "买入"),
    SELL("SELL", "卖出"),
    HOLD("HOLD", "持有");

    private final String code;
    private final String desc;
}
