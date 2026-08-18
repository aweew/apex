package com.awe.apex.quant.domain.bo;

import com.awe.apex.quant.domain.dto.IntradayAcceptanceMetric;
import com.awe.apex.quant.domain.dto.ScreenerMarketSnapshot;
import com.awe.apex.quant.domain.dto.ScreenerRuleEvidenceResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.LimitUpPool;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 策略执行过程中的股票候选
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenerCandidateBO {

    /** 实时截面 */
    private ScreenerMarketSnapshot snapshot;

    /** 升序日线 */
    private List<BarDaily> bars;

    /** 当日涨停池记录 */
    private LimitUpPool limitUpPool;

    /** 同题材涨停家数 */
    private Integer themeLinkageCount;

    /** 日线截止日期 */
    private LocalDate dailyAsOf;

    /** 区间涨跌幅 */
    private BigDecimal rangeReturn;

    /** 近期涨停次数 */
    private Integer limitUpCount;

    /** 连续上涨天数 */
    private Integer upDays;

    /** 20日相对强度 */
    private BigDecimal rs20;

    /** ATR占现价比例 */
    private BigDecimal atrPct;

    /** 区间价格位置 */
    private BigDecimal pricePosition;

    /** 距最近涨停交易日数 */
    private Integer daysSinceLimitUp;

    /** 最新成交量相对前期均量百分比 */
    private BigDecimal volumeMaRatio;

    /** 最新收盘价相对均线距离百分比 */
    private BigDecimal closeMaDistancePct;

    /** 是否突破前期高点 */
    private Boolean breakoutPreviousHigh;

    /** MA5、MA10、MA20是否多头排列 */
    private Boolean maBullishAlignment;

    /** 分时承接指标 */
    private IntradayAcceptanceMetric intradayMetric;

    /** 已通过规则的命中依据 */
    private List<ScreenerRuleEvidenceResp> evidence;
}
