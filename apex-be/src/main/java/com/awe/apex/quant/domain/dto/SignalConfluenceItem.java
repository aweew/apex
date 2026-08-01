package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 多策略共振信号
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalConfluenceItem {

    /**
     * 代码
     */
    private String code;

    /**
     * 方向
     */
    private String side;

    /**
     * 共振策略数
     */
    private Integer strategyCount;

    /**
     * 策略列表
     */
    private List<String> strategies;

    /**
     * 平均评分
     */
    private BigDecimal avgScore;

    /**
     * 最高评分
     */
    private BigDecimal maxScore;
}
