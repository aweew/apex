package com.awe.apex.quant.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 个股资金流日快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("stock_fund_flow")
public class StockFundFlow {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 证券代码 */
    private String code;

    /** 证券名称 */
    private String name;

    /** 交易日 */
    private LocalDate tradeDate;

    /** 涨跌幅百分比 */
    private BigDecimal pctChg;

    /** 主力净流入元 */
    private BigDecimal mainNetInflow;

    /** 主力净流入占比百分比 */
    private BigDecimal mainNetInflowPct;

    /** 超大单净流入元 */
    private BigDecimal superLargeNetInflow;

    /** 大单净流入元 */
    private BigDecimal largeNetInflow;

    /** 中单净流入元 */
    private BigDecimal mediumNetInflow;

    /** 小单净流入元 */
    private BigDecimal smallNetInflow;

    /** 同步时间 */
    private LocalDateTime syncedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;
}
