package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 权益曲线点
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EquityPointResp {

    /**
     * 日期
     */
    private LocalDate tradeDate;

    /**
     * 权益
     */
    private BigDecimal equity;
}
