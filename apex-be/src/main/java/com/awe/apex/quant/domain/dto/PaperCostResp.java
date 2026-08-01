package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 纸面交易成本汇总
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaperCostResp {

    /**
     * 账户ID
     */
    private Long accountId;

    /**
     * 累计手续费
     */
    private BigDecimal totalFee;

    /**
     * 累计成交额
     */
    private BigDecimal totalTurnover;

    /**
     * 费用率（fee/turnover）
     */
    private BigDecimal feeRate;

    /**
     * 订单笔数
     */
    private Integer orderCount;

    /**
     * 买入笔数
     */
    private Integer buyCount;

    /**
     * 卖出笔数
     */
    private Integer sellCount;

    /**
     * 费用占初始资金比例
     */
    private BigDecimal feeToCapital;

    /**
     * 说明
     */
    private String message;
}
