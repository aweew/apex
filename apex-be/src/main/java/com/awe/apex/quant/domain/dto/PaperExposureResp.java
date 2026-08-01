package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 持仓暴露与集中度
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaperExposureResp {

    /**
     * 总资产
     */
    private BigDecimal totalAsset;

    /**
     * 现金占比
     */
    private BigDecimal cashWeight;

    /**
     * 股票仓位占比
     */
    private BigDecimal equityWeight;

    /**
     * 第一大持仓权重
     */
    private BigDecimal top1Weight;

    /**
     * 前五大持仓权重合计
     */
    private BigDecimal top5Weight;

    /**
     * 赫芬达尔集中度（持仓权重平方和）
     */
    private BigDecimal herfindahl;

    /**
     * 行业暴露
     */
    private List<IndustryPnlResp> industries;

    /**
     * 单票暴露（按市值降序）
     */
    private List<PositionWeightResp> positions;
}
