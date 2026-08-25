package com.awe.apex.quant.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 盘前涨跌比预测规则输出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketBreadthForecastEvalResp {

    /** 预测上涨占比，平盘剔除后计算，单位百分比。 */
    private BigDecimal predictedUpRatio;

    /** 预测下跌占比，平盘剔除后计算，单位百分比。 */
    private BigDecimal predictedDownRatio;

    /** 规则合成分数，等于预测上涨占比。 */
    private BigDecimal score;

    /** 预测置信度，高/中/低。 */
    private String confidence;

    /** 本次预测实际采用的依据。 */
    private List<String> reasons;
}
