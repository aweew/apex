package com.awe.apex.quant.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 交易记录业务来源。
 */
@Getter
@AllArgsConstructor
public enum PortfolioTradeSourceEnum {

    MANUAL("MANUAL", "人工录入"),
    DAILY_ACTION("DAILY_ACTION", "日终清单"),
    PORTFOLIO_WEB("PORTFOLIO_WEB", "组合维护"),
    PORTFOLIO_IMPORT("PORTFOLIO_IMPORT", "组合文本导入"),
    HOLDING_WEB("HOLDING_WEB", "真实持仓维护"),
    WECHAT_BOT("WECHAT_BOT", "微信 Bot");

    /** 来源编码 */
    private final String code;

    /** 来源说明 */
    private final String desc;
}
