package com.awe.apex.quant.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 财务分析指标（宽表关键字段 + payload 全量）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("stock_fin_indicator")
public class StockFinIndicator implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 证券代码
     */
    private String code;

    /**
     * 报告期
     */
    private LocalDate reportDate;

    /**
     * 摊薄每股收益
     */
    private BigDecimal eps;

    /**
     * 加权每股收益
     */
    private BigDecimal epsWeighted;

    /**
     * 调整后每股收益
     */
    private BigDecimal epsAdjusted;

    /**
     * 扣非每股收益
     */
    private BigDecimal epsExcl;

    /**
     * 每股净资产
     */
    private BigDecimal bps;

    /**
     * 每股经营现金流
     */
    private BigDecimal ocfps;

    /**
     * 每股资本公积
     */
    private BigDecimal capitalReservePs;

    /**
     * 每股未分配利润
     */
    private BigDecimal undistributedPs;

    /**
     * 净资产收益率%
     */
    private BigDecimal roe;

    /**
     * 总资产净利率%
     */
    private BigDecimal roa;

    /**
     * 销售毛利率%
     */
    private BigDecimal grossMargin;

    /**
     * 销售净利率%
     */
    private BigDecimal netMargin;

    /**
     * 营业利润率%
     */
    private BigDecimal operateMargin;

    /**
     * 资产负债率%
     */
    private BigDecimal debtRatio;

    /**
     * 流动比率
     */
    private BigDecimal currentRatio;

    /**
     * 速动比率
     */
    private BigDecimal quickRatio;

    /**
     * 全量指标 JSON
     */
    private String payload;

    /**
     * 数据来源
     */
    private String source;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除
     */
    @TableLogic
    private Integer deleted;
}
