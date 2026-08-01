package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 单票仓位权重
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionWeightResp {

    /**
     * 代码
     */
    private String code;

    /**
     * 名称
     */
    private String name;

    /**
     * 市值
     */
    private BigDecimal marketValue;

    /**
     * 占总资产权重
     */
    private BigDecimal weight;

    /**
     * 浮盈亏
     */
    private BigDecimal pnl;

    /**
     * 行业
     */
    private String industry;

    /**
     * 浮盈贡献占比
     */
    private BigDecimal pnlContribution;
}
