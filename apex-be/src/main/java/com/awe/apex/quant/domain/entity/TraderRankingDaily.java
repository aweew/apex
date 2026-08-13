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

/** Smart Trader 每日排名。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("trader_ranking_daily")
public class TraderRankingDaily implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    /** 主键 */ @TableId(type = IdType.AUTO) private Long id;
    /** 交易日 */ private LocalDate tradeDate;
    /** 交易者ID */ private Long traderId;
    /** 累计收益率 */ private BigDecimal totalReturn;
    /** 当日收益率 */ private BigDecimal dailyReturn;
    /** 最大回撤 */ private BigDecimal maxDrawdown;
    /** 胜率 */ private BigDecimal winRate;
    /** 盈亏比 */ private BigDecimal profitLossRatio;
    /** 夏普率 */ private BigDecimal sharpe;
    /** 综合评分 */ private BigDecimal traderScore;
    /** 累计收益排名 */ private Integer returnRanking;
    /** 当日收益排名 */ private Integer dailyRanking;
    /** 稳健排名 */ private Integer steadyRanking;
    /** 创建时间 */ private LocalDateTime createTime;
    /** 更新时间 */ private LocalDateTime updateTime;
    /** 逻辑删除 */ @TableLogic private Integer deleted;
}
