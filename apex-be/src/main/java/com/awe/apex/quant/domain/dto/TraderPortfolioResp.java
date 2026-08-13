package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 交易者账户响应。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TraderPortfolioResp {
    /** 交易者ID */ private Long traderId;
    /** 交易日 */ private LocalDate tradeDate;
    /** 现金 */ private BigDecimal cash;
    /** 持仓市值 */ private BigDecimal marketValue;
    /** 总资产 */ private BigDecimal totalAsset;
    /** 当日收益率 */ private BigDecimal dailyProfitRate;
    /** 累计收益率 */ private BigDecimal totalProfitRate;
    /** 最大回撤 */ private BigDecimal maxDrawdown;
    /** 当前持仓 */ private List<TraderPositionResp> positions;
}
