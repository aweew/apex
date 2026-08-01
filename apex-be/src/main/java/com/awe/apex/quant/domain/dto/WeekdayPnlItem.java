package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 按卖出星期几的盈亏
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeekdayPnlItem {

    /**
     * 星期（1=周一 … 5=周五）
     */
    private Integer weekday;

    /**
     * 标签
     */
    private String label;

    /**
     * 闭合笔数
     */
    private Integer tradeCount;

    /**
     * 胜率
     */
    private BigDecimal winRate;

    /**
     * 平均收益率
     */
    private BigDecimal avgReturn;

    /**
     * 累计收益率（等权平均之和）
     */
    private BigDecimal sumReturn;
}
