package com.awe.apex.quant.domain.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 盘前研报个股观点。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreMarketStockPickBO {

    /**
     * 推荐顺序。
     */
    private Integer rank;

    /**
     * 推荐级别。
     */
    private String level;

    /**
     * 股票代码。
     */
    private String code;

    /**
     * 股票名称。
     */
    private String name;

    /**
     * 个股所属的当日主线方向。
     */
    private String direction;

    /**
     * 带取舍的主观观点。
     */
    private String opinion;

    /**
     * 支撑观点的已知事实。
     */
    private String evidence;

    /**
     * 允许执行的确认条件。
     */
    private String trigger;

    /**
     * 判断失效或离场条件。
     */
    private String invalidation;

    /**
     * 建议仓位，取值范围为 0 到 1。
     */
    private BigDecimal suggestedWeight;
}
