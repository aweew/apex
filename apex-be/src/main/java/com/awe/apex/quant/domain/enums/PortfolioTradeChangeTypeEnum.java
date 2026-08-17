package com.awe.apex.quant.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 组合持仓变动类型。
 */
@Getter
@AllArgsConstructor
public enum PortfolioTradeChangeTypeEnum {

    OPEN("OPEN", "建仓"),
    ADD("ADD", "加仓"),
    REDUCE("REDUCE", "减仓"),
    CLEAR("CLEAR", "清仓"),
    MANUAL("MANUAL", "人工成交");

    /** 类型编码 */
    private final String code;

    /** 类型说明 */
    private final String desc;
}
