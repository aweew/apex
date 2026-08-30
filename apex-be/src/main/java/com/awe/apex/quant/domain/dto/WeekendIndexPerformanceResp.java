package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 周末研报指数周度表现。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeekendIndexPerformanceResp {

    /** 指数代码。 */
    private String code;

    /** 指数名称。 */
    private String name;

    /** 周初基准收盘价，即前一交易日收盘价。 */
    private BigDecimal weekStartClose;

    /** 周五收盘价。 */
    private BigDecimal weekEndClose;

    /** 上周收益率（百分比）。 */
    private BigDecimal weeklyReturn;

    /** 周五涨跌幅（百分比）。 */
    private BigDecimal fridayPctChg;
}
