package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 策略绩效榜单项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyLeaderboardItemResp {

    /**
     * 策略ID
     */
    private String strategyId;

    /**
     * 成功回测次数
     */
    private Integer jobCount;

    /**
     * 平均收益
     */
    private BigDecimal avgReturn;

    /**
     * 平均夏普
     */
    private BigDecimal avgSharpe;

    /**
     * 平均最大回撤
     */
    private BigDecimal avgMaxDrawdown;

    /**
     * 最佳收益
     */
    private BigDecimal bestReturn;

    /**
     * 最差收益
     */
    private BigDecimal worstReturn;
}
