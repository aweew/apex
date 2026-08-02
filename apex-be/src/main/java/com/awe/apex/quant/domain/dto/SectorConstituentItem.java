package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 板块成分股项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectorConstituentItem {

    /**
     * 证券代码
     */
    private String code;

    /**
     * 名称
     */
    private String name;

    /**
     * 最新价
     */
    private BigDecimal latestPrice;

    /**
     * 涨跌幅%
     */
    private BigDecimal pctChg;
}
