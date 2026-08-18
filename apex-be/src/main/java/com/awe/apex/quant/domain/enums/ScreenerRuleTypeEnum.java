package com.awe.apex.quant.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 选股规则类型
 */
@Getter
@AllArgsConstructor
public enum ScreenerRuleTypeEnum {

    MARKET_BOARD("MARKET_BOARD", "市场板块"),
    EXCLUDE_ST("EXCLUDE_ST", "排除ST"),
    PE_TTM("PE_TTM", "滚动市盈率"),
    PB("PB", "市净率"),
    TOTAL_MV("TOTAL_MV", "总市值"),
    CIRC_MV("CIRC_MV", "流通市值"),
    PCT_CHG("PCT_CHG", "当日涨跌幅"),
    TURNOVER_RATE("TURNOVER_RATE", "当日换手率"),
    VOLUME_RATIO("VOLUME_RATIO", "实时量比"),
    RANGE_RETURN("RANGE_RETURN", "区间涨跌幅"),
    LIMIT_UP_COUNT("LIMIT_UP_COUNT", "近期涨停次数"),
    UP_DAYS("UP_DAYS", "连续上涨天数"),
    RS20("RS20", "20日相对强度"),
    ATR_PCT("ATR_PCT", "ATR14占现价比例"),
    PRICE_POSITION("PRICE_POSITION", "区间价格位置"),
    DAYS_SINCE_LIMIT_UP("DAYS_SINCE_LIMIT_UP", "距最近涨停天数"),
    VOLUME_MA_RATIO("VOLUME_MA_RATIO", "量能相对均量"),
    CLOSE_MA_DISTANCE_PCT("CLOSE_MA_DISTANCE_PCT", "收盘相对均线距离"),
    BREAKOUT_PREVIOUS_HIGH("BREAKOUT_PREVIOUS_HIGH", "突破前期高点"),
    MA_BULLISH_ALIGNMENT("MA_BULLISH_ALIGNMENT", "均线多头排列"),
    INTRADAY_ABOVE_AVG_RATIO("INTRADAY_ABOVE_AVG_RATIO", "分时均价线上方占比"),
    INTRADAY_CURRENT_ABOVE_AVG("INTRADAY_CURRENT_ABOVE_AVG", "当前价不低于分时均价"),
    INTRADAY_MAX_BELOW_MINUTES("INTRADAY_MAX_BELOW_MINUTES", "连续跌破均价分钟数"),
    LIMIT_UP_LEVEL("LIMIT_UP_LEVEL", "连板层级"),
    FIRST_SEAL_TIME("FIRST_SEAL_TIME", "首次封板时间"),
    LAST_SEAL_TIME("LAST_SEAL_TIME", "最后封板时间"),
    BREAK_COUNT("BREAK_COUNT", "炸板次数"),
    SEAL_AMOUNT("SEAL_AMOUNT", "封单金额"),
    AMOUNT("AMOUNT", "成交额"),
    THEME_LINKAGE_COUNT("THEME_LINKAGE_COUNT", "同题材涨停家数");

    private final String code;
    private final String desc;
}
