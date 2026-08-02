package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 大盘指数快照项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketIndexItem {

    /**
     * 显示名
     */
    private String name;

    /**
     * 涨跌幅 %
     */
    private BigDecimal pctChg;

    /**
     * 收盘点位
     */
    private BigDecimal close;

    /**
     * 方向：up / down / flat
     */
    private String direction;
}
