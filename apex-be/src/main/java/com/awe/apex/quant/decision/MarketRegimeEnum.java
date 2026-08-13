package com.awe.apex.quant.decision;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 市场状态
 */
@Getter
@AllArgsConstructor
public enum MarketRegimeEnum {

    ATTACK("ATTACK", "进攻", new BigDecimal("0.70"), new BigDecimal("0.0075")),
    BALANCE("BALANCE", "均衡", new BigDecimal("0.50"), new BigDecimal("0.005")),
    DEFENSE("DEFENSE", "防守", new BigDecimal("0.20"), new BigDecimal("0.0025")),
    UNKNOWN("UNKNOWN", "未知", new BigDecimal("0.20"), BigDecimal.ZERO);

    /**
     * 状态编码
     */
    private final String code;

    /**
     * 状态说明
     */
    private final String desc;

    /**
     * 总仓上限
     */
    private final BigDecimal totalExposureLimit;

    /**
     * 单笔权益风险比例
     */
    private final BigDecimal singleRiskRate;
}
