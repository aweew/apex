package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 风控概览
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskOverviewResp {

    /**
     * 账户ID
     */
    private Long accountId;

    /**
     * 总资产
     */
    private BigDecimal totalAsset;

    /**
     * 可用资金
     */
    private BigDecimal cash;

    /**
     * 持仓市值
     */
    private BigDecimal positionValue;

    /**
     * 总仓位比例
     */
    private BigDecimal positionRatio;

    /**
     * 总仓位上限
     */
    private BigDecimal totalLimit;

    /**
     * 单票上限
     */
    private BigDecimal singleLimit;

    /**
     * 同行业上限
     */
    private BigDecimal industryLimit;

    /**
     * 预警列表
     */
    private List<String> warnings;

    /**
     * 分级告警
     */
    private List<RiskAlertItem> alerts;

    /**
     * CRITICAL 数量
     */
    private Integer criticalCount;

    /**
     * WARN 数量
     */
    private Integer warnCount;
}
