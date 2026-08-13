package com.awe.apex.quant.decision;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 市场状态评估结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketRegimeResult {

    /**
     * 市场状态
     */
    private MarketRegimeEnum marketRegime;

    /**
     * 总仓上限
     */
    private BigDecimal totalExposureLimit;

    /**
     * 评估原因
     */
    private String reason;
}
