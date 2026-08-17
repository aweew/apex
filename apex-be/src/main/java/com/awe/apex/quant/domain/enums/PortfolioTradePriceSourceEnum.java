package com.awe.apex.quant.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 交易价格来源。
 */
@Getter
@AllArgsConstructor
public enum PortfolioTradePriceSourceEnum {

    USER_REPORTED("USER_REPORTED", "用户填写"),
    BOT_REPORTED("BOT_REPORTED", "Bot 指令提供"),
    MARKET_SNAPSHOT("MARKET_SNAPSHOT", "行情快照估算"),
    DAILY_CLOSE("DAILY_CLOSE", "最近收盘价估算"),
    UNAVAILABLE("UNAVAILABLE", "暂无价格");

    /** 来源编码 */
    private final String code;

    /** 来源说明 */
    private final String desc;
}
