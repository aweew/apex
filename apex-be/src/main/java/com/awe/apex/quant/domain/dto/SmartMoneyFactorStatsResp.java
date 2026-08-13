package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Smart Money 因子事后样本统计。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmartMoneyFactorStatsResp {
    /** 持有期交易日 */ private Integer holdingDays;
    /** 有效样本数 */ private Integer sampleCount;
    /** 平均收益率 */ private BigDecimal averageReturn;
    /** 正收益样本比例 */ private BigDecimal winRate;
    /** 因子值与收益的 Pearson IC */ private BigDecimal informationCoefficient;
}
