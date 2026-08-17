package com.awe.apex.quant.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 选股策略运行模式
 */
@Getter
@AllArgsConstructor
public enum ScreenerRunModeEnum {

    REALTIME("REALTIME", "实时"),
    CLOSE("CLOSE", "收盘");

    private final String code;
    private final String desc;
}
