package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 持仓周期分桶
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldBucketItem {

    /**
     * 分桶标签
     */
    private String bucket;

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
     * 累计盈亏贡献（相对买入金额）
     */
    private BigDecimal totalReturn;
}
