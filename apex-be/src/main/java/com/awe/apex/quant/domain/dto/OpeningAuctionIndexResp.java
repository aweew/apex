package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 集合竞价指数报价。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpeningAuctionIndexResp {

    /**
     * 腾讯行情代码。
     */
    private String code;

    /**
     * 指数名称。
     */
    private String name;

    /**
     * 当前竞价指示价或开盘确认价。
     */
    private BigDecimal latestPrice;

    /**
     * 相对昨收涨跌幅百分比。
     */
    private BigDecimal pctChg;

    /**
     * 本次报价读取时间。
     */
    private LocalDateTime quoteTime;
}
