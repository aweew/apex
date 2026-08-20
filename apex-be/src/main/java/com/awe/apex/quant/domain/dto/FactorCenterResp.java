package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 个股因子中心响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FactorCenterResp {

    /**
     * 证券代码
     */
    private String code;

    /**
     * 证券名称
     */
    private String name;

    /**
     * 市场
     */
    private String market;

    /**
     * 所属行业
     */
    private String industry;

    /**
     * 最新价格
     */
    private BigDecimal latestPrice;

    /**
     * 日线截止日期
     */
    private LocalDate asOf;

    /**
     * Alpha 综合分
     */
    private BigDecimal alphaScore;

    /**
     * Alpha 可用权重覆盖率
     */
    private BigDecimal coverage;

    /**
     * Alpha 评分标签
     */
    private String alphaLabel;

    /**
     * 评分模型版本
     */
    private String scoreModel;

    /**
     * Alpha 评分组成
     */
    private List<AlphaComponentResp> alphaComponents;

    /**
     * 六类因子
     */
    private List<FactorCategoryResp> categories;

    /**
     * 数据说明
     */
    private String message;
}
