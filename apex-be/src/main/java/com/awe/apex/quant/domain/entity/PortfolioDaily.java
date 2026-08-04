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
 * 组合每日快照
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("portfolio_daily")
public class PortfolioDaily implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 组合ID
     */
    private Long portfolioId;

    /**
     * 交易日
     */
    private LocalDate tradeDate;

    /**
     * 市值
     */
    private BigDecimal marketValue;

    /**
     * 成本市值
     */
    private BigDecimal costValue;

    /**
     * 累计浮盈
     */
    private BigDecimal totalPnl;

    /**
     * 当日浮盈
     */
    private BigDecimal todayPnl;

    /**
     * 当日涨跌幅%
     */
    private BigDecimal todayPct;

    /**
     * 持仓只数
     */
    private Integer positionCount;

    /**
     * 现金（预留）
     */
    private BigDecimal cash;

    /**
     * 持仓明细JSON
     */
    private String payload;

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
