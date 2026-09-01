package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 盘后明星个股。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostMarketStarStockResp {

    /** 证券代码。 */
    private String code;

    /** 证券名称。 */
    private String name;

    /** 当日涨跌幅百分比。 */
    private BigDecimal pctChg;

    /** 最新价。 */
    private BigDecimal latestPrice;

    /** 换手率百分比。 */
    private BigDecimal turnoverRate;

    /** 连板数，首板为 1。 */
    private Integer lianban;

    /** 封板资金，元。 */
    private BigDecimal sealAmount;

    /** 主力净流入，元。 */
    private BigDecimal mainNetInflow;

    /** 主力净流入占比百分比。 */
    private BigDecimal mainNetInflowPct;

    /** 所属行业。 */
    private String industry;

    /** 展示题材。 */
    private String theme;

    /** 入选原因。 */
    @Builder.Default
    private List<String> reasons = new ArrayList<>();
}
