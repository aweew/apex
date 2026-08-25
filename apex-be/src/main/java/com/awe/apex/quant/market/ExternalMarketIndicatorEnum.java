package com.awe.apex.quant.market;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 盘前外围市场观察指标。
 */
@Getter
@AllArgsConstructor
public enum ExternalMarketIndicatorEnum {

    GOLD("GOLD", "黄金", "GC=F",
            "黄金上涨常表示避险需求升温，黄金股可能更受关注；若同时股市走弱，整体风险偏好可能下降。",
            "黄金回落说明避险需求减弱，黄金股的短期情绪可能降温；仍需结合美元和利率观察。"),
    CRUDE_OIL("CRUDE_OIL", "原油", "CL=F",
            "原油上涨通常利好油气，也会抬高化工、航空等成本；涨得过快还可能增加通胀压力。",
            "原油回落可缓解部分成本压力，油气股短期情绪可能降温；不能单独判断大盘方向。"),
    DOLLAR_INDEX("DOLLAR_INDEX", "美元指数", "DX-Y.NYB",
            "美元指数上涨往往表示全球资金更谨慎，对成长股估值和外资情绪偏压制。",
            "美元指数回落通常有利于风险偏好修复，对成长股估值和外资情绪相对友好。"),
    OFFSHORE_RENMINBI("OFFSHORE_RENMINBI", "离岸人民币", "USDCNH=X",
            "离岸人民币数值上升表示人民币走弱，通常会增加外资观望和进口成本。",
            "离岸人民币数值回落表示人民币走强，通常有利于稳定外资情绪和进口成本预期。"),
    US_TREASURY_10Y("US_TREASURY_10Y", "美国10年期国债收益率", "^TNX",
            "美债收益率上升意味着美元无风险回报变高，通常压低成长股估值。",
            "美债收益率回落会减轻成长股估值压力，对风险偏好相对友好。");

    /**
     * 指标编码。
     */
    private final String code;

    /**
     * 指标名称。
     */
    private final String desc;

    /**
     * 行情源标识。
     */
    private final String sourceSymbol;

    /**
     * 指标上涨时对 A 股的传导说明。
     */
    private final String riseImpact;

    /**
     * 指标回落时对 A 股的传导说明。
     */
    private final String fallImpact;

    /**
     * 兼容既有调用的默认传导说明。
     *
     * @return 指标上涨时的传导说明
     */
    public String getAShareImpact() {
        return riseImpact;
    }
}
