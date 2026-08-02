package com.awe.apex.quant.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
 * 板块行情快照
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sector_quote")
public class SectorQuote implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 板块代码
     */
    private String code;

    /**
     * 板块名称
     */
    private String name;

    /**
     * 类型 INDUSTRY/CONCEPT/THEME
     */
    private String boardType;

    /**
     * 交易日
     */
    private LocalDate tradeDate;

    /**
     * 涨跌幅%
     */
    private BigDecimal pctChg;

    /**
     * 近3日涨跌幅%
     */
    @TableField("pct_chg_3d")
    private BigDecimal pctChg3d;

    /**
     * 近5日涨跌幅%
     */
    @TableField("pct_chg_5d")
    private BigDecimal pctChg5d;

    /**
     * 资金净流入（元）
     */
    private BigDecimal netInflow;

    /**
     * 主力净流入（元）
     */
    private BigDecimal mainNetInflow;

    /**
     * 成交额（元）
     */
    private BigDecimal amount;

    /**
     * 上涨家数
     */
    private Integer upCount;

    /**
     * 下跌家数
     */
    private Integer downCount;

    /**
     * 涨停家数
     */
    private Integer limitUpCount;

    /**
     * 连板高度
     */
    private Integer maxLianban;

    /**
     * 领涨股代码
     */
    private String leadStockCode;

    /**
     * 领涨股名称
     */
    private String leadStockName;

    /**
     * 领涨股涨跌幅%
     */
    private BigDecimal leadStockPct;

    /**
     * 涨跌原因摘要
     */
    private String moveReason;

    /**
     * 同步时间
     */
    private LocalDateTime syncedAt;

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
