package com.awe.apex.quant.domain.enums;

import com.awe.apex.common.util.StringUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 可统计的用户功能模块。
 */
@Getter
@AllArgsConstructor
public enum UserUsageModuleEnum {

    AUTH("AUTH", "账户登录"),
    DASHBOARD("DASHBOARD", "决策看板"),
    PRE_MARKET_REPORT("PRE_MARKET_REPORT", "盘前研报"),
    AI_CENTER("AI_CENTER", "小灵"),
    DECISION("DECISION", "智能决策"),
    MARKET("MARKET", "行情中心"),
    LIMIT_UP("LIMIT_UP", "连板天梯"),
    SYNC("SYNC", "同步中心"),
    HOT("HOT", "市场热点"),
    NEWS("NEWS", "财经资讯"),
    HOLDING("HOLDING", "我的持仓"),
    PORTFOLIO("PORTFOLIO", "投资组合"),
    TRADES("TRADES", "交易记录"),
    OBSERVE("OBSERVE", "观察池"),
    PIPELINE("PIPELINE", "决策流水线"),
    SCREENER("SCREENER", "股票筛选"),
    VALUATION("VALUATION", "估值分析"),
    WATCHLIST("WATCHLIST", "自选股"),
    STOCK("STOCK", "股票详情"),
    SIGNALS("SIGNALS", "策略信号"),
    BACKTEST("BACKTEST", "策略回测"),
    PAPER("PAPER", "模拟交易"),
    DAILY("DAILY", "每日行动"),
    CONFIG("CONFIG", "系统参数"),
    USAGE("USAGE", "使用统计");

    private final String code;
    private final String desc;

    /**
     * 根据模块编码查询枚举。
     *
     * @param code 模块编码
     * @return 模块枚举，不存在时返回空
     */
    public static UserUsageModuleEnum findByCode(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        for (UserUsageModuleEnum module : values()) {
            if (module.getCode().equals(code.trim())) {
                return module;
            }
        }
        return null;
    }
}
