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

/** Smart Trader 每日账户快照。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("trader_portfolio_daily")
public class TraderPortfolioDaily implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    /** 主键 */ @TableId(type = IdType.AUTO) private Long id;
    /** 交易者ID */ private Long traderId;
    /** 交易日 */ private LocalDate tradeDate;
    /** 现金 */ private BigDecimal cash;
    /** 持仓市值 */ private BigDecimal marketValue;
    /** 总资产 */ private BigDecimal totalAsset;
    /** 当日损益 */ private BigDecimal dailyProfit;
    /** 当日收益率 */ private BigDecimal dailyProfitRate;
    /** 累计损益 */ private BigDecimal totalProfit;
    /** 累计收益率 */ private BigDecimal totalProfitRate;
    /** 最大回撤 */ private BigDecimal maxDrawdown;
    /** 创建时间 */ private LocalDateTime createTime;
    /** 更新时间 */ private LocalDateTime updateTime;
    /** 逻辑删除 */ @TableLogic private Integer deleted;
}
