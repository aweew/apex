package com.awe.apex.quant.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 同花顺股票行情快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HithinkSnapshotItem {

    /**
     * 带交易所后缀的标的代码。
     */
    private String thscode;

    /**
     * 纯股票代码。
     */
    private String ticker;

    /**
     * 最新价。
     */
    private BigDecimal lastPrice;

    /**
     * 涨跌幅，百分比数值。
     */
    private BigDecimal priceChangeRatioPct;

    /**
     * 数据时间，毫秒 Unix 时间戳。
     */
    private Long timestamp;
}
