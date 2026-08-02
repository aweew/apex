package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 个股分时
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockIntradayResp {

    /**
     * 证券代码
     */
    private String code;

    /**
     * 名称
     */
    private String name;

    /**
     * 昨收
     */
    private BigDecimal preClose;

    /**
     * 交易日（分时所属日）
     */
    private String tradeDate;

    /**
     * 分时点
     */
    private List<IntradayPoint> points;

    /**
     * 说明
     */
    private String note;
}
