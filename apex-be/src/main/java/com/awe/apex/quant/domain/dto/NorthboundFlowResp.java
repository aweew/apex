package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 北向资金响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NorthboundFlowResp {

    /** 交易日 */
    private LocalDate tradeDate;

    /** 北向资金净买额元 */
    private BigDecimal netBuyAmount;

    /** 北向资金买入额元 */
    private BigDecimal buyAmount;

    /** 北向资金卖出额元 */
    private BigDecimal sellAmount;

    /** 北向资金累计净买额元 */
    private BigDecimal cumulativeNetBuyAmount;

    /** 数据状态PUBLISHED已披露NOT_DISCLOSED未披露 */
    private String dataStatus;

    /** 同步时间 */
    private LocalDateTime syncedAt;
}
