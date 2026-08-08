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
 * 股票基础信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("stock_basic")
public class StockBasic implements Serializable {

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
     * 证券简称
     */
    private String name;

    /**
     * 市场 SH/SZ/BJ
     */
    private String market;

    /**
     * 上市日期
     */
    private LocalDate listDate;

    /**
     * 是否ST 0否1是
     */
    private Integer stFlag;

    /**
     * 动态市盈率（按预测全年利润）
     */
    private BigDecimal peDynamic;

    /**
     * 静态市盈率（按上一完整年度利润）
     */
    private BigDecimal peStatic;

    /**
     * 滚动市盈率（按最近四个季度利润）
     */
    private BigDecimal peTtm;

    /**
     * 市净率
     */
    private BigDecimal pb;

    /**
     * 总市值
     */
    private BigDecimal totalMv;

    /**
     * 流通市值
     */
    private BigDecimal circMv;

    /**
     * 行业
     */
    private String industry;

    /**
     * 最新价
     */
    private BigDecimal latestPrice;

    /**
     * 涨跌幅%
     */
    private BigDecimal pctChg;

    /**
     * 数据来源
     */
    private String source;

    /**
     * 行情更新时间
     */
    private LocalDateTime quoteTime;

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
