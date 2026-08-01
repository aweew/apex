package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 单笔成交质量
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FillQualityItem {

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 代码
     */
    private String code;

    /**
     * 方向
     */
    private String side;

    /**
     * 成交日
     */
    private LocalDate tradeDate;

    /**
     * 成交价
     */
    private BigDecimal fillPrice;

    /**
     * 当日收盘
     */
    private BigDecimal closePrice;

    /**
     * 相对收盘滑点
     */
    private BigDecimal slippageVsClose;
}
