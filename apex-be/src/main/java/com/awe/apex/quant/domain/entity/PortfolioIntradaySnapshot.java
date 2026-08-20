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
 * 组合盘中收益快照
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("portfolio_intraday_snapshot")
public class PortfolioIntradaySnapshot implements Serializable {

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
     * 五分钟快照时间
     */
    private LocalDateTime snapshotTime;

    /**
     * 组合总权益
     */
    private BigDecimal totalEquity;

    /**
     * 当日盈亏
     */
    private BigDecimal todayPnl;

    /**
     * 当日收益率百分比
     */
    private BigDecimal todayPct;

    /**
     * 持仓只数
     */
    private Integer positionCount;

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
