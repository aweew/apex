package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 板块榜单项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectorBoardItem {

    /**
     * 板块代码
     */
    private String code;

    /**
     * 板块名称
     */
    private String name;

    /**
     * 类型
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
    private BigDecimal pctChg3d;

    /**
     * 近5日涨跌幅%
     */
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
     * 主线评分（仅主线接口填充）
     */
    private BigDecimal mainlineScore;

    /**
     * 同步时间
     */
    private LocalDateTime syncedAt;
}
