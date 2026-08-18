package com.awe.apex.quant.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 看板指挥交易阶段。
 */
@Getter
@AllArgsConstructor
public enum DashboardCommandPhaseEnum {

    PRE_MARKET("PRE_MARKET", "盘前决策"),
    IN_SESSION("IN_SESSION", "今日操作指引"),
    AFTER_CLOSE("AFTER_CLOSE", "今日计划与收盘状态"),
    NON_TRADING_DAY("NON_TRADING_DAY", "下个交易日准备");

    /** 阶段编码 */
    private final String code;

    /** 阶段说明 */
    private final String desc;
}
