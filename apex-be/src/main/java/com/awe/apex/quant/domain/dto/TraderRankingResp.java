package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 交易者排名响应。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TraderRankingResp {
    /** 排名 */ private Integer ranking;
    /** 交易者ID */ private Long traderId;
    /** 交易者名称 */ private String traderName;
    /** 累计收益率 */ private BigDecimal totalReturn;
    /** 当日收益率 */ private BigDecimal dailyReturn;
    /** 最大回撤 */ private BigDecimal maxDrawdown;
    /** 综合评分 */ private BigDecimal traderScore;
}
