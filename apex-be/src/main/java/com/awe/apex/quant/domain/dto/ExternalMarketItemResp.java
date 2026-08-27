package com.awe.apex.quant.domain.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 外围市场指标及其对 A 股的传导说明。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalMarketItemResp {

    /**
     * 指标编码。
     */
    private String code;

    /**
     * 指标名称。
     */
    private String name;

    /**
     * 当前指标是否成功获取报价。
     */
    private boolean available;

    /**
     * 最新报价或收益率。
     */
    private BigDecimal latestPrice;

    /**
     * 相对前一交易日的涨跌幅百分比。
     */
    private BigDecimal pctChg;

    /**
     * 报价时间。
     */
    private LocalDateTime quoteTime;

    /**
     * 行情来源。
     */
    private String source;

    /**
     * 面向新手的 A 股影响说明。
     */
    @JsonAlias("ashareImpact")
    @JsonProperty("aShareImpact")
    private String aShareImpact;
}
