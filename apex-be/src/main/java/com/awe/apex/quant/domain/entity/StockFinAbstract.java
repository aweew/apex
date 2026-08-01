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
 * 同花顺财务摘要
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("stock_fin_abstract")
public class StockFinAbstract implements Serializable {

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
     * 净利润
     */
    private BigDecimal netProfit;

    /**
     * 净利润同比%
     */
    private BigDecimal netProfitYoy;

    /**
     * 扣非净利润
     */
    private BigDecimal netProfitExcl;

    /**
     * 扣非净利润同比%
     */
    private BigDecimal netProfitExclYoy;

    /**
     * 营业总收入
     */
    private BigDecimal revenue;

    /**
     * 营收同比%
     */
    private BigDecimal revenueYoy;

    /**
     * 基本每股收益
     */
    private BigDecimal epsBasic;

    /**
     * 每股净资产
     */
    private BigDecimal bps;

    /**
     * 每股经营现金流
     */
    private BigDecimal ocfps;

    /**
     * 销售净利率%
     */
    private BigDecimal netMargin;

    /**
     * 净资产收益率%
     */
    private BigDecimal roe;

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
     * 全量摘要 JSON
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
