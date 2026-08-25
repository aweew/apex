package com.awe.apex.quant.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 盘前涨跌比预测规则输入。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketBreadthForecastEvalReq {

    /** 上一交易日上涨占比，平盘剔除后计算，单位百分比。 */
    private BigDecimal previousBreadthUpRatio;

    /** 隔夜主要指数平均涨跌幅，单位百分比。 */
    private BigDecimal overnightIndexPctChg;

    /** 富时 A50 期指涨跌幅，单位百分比。 */
    private BigDecimal ftseA50PctChg;

    /** 亚太主要指数平均涨跌幅，单位百分比。 */
    private BigDecimal asiaIndexPctChg;

    /** 当日盘前利好消息数量。 */
    private Integer newsBullCount;

    /** 当日盘前利空消息数量。 */
    private Integer newsBearCount;

    /** 已结算历史样本推导的校准值，单位百分点。 */
    private BigDecimal calibrationAdjustment;
}
