package com.awe.apex.quant.signal.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 市场行为排行项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalRankingItemResp {

    /** 事件ID */
    private Long eventId;

    /** 证券代码 */
    private String symbol;

    /** 证券名称 */
    private String name;

    /** 信号编码 */
    private String signalCode;

    /** 信号名称 */
    private String signalName;

    /** 信号方向 */
    private String direction;

    /** 生命周期状态 */
    private String lifecycleState;

    /** 行为强度 */
    private BigDecimal strength;

    /** 置信度 */
    private BigDecimal confidence;

    /** 历史条件概率 */
    private BigDecimal probability;

    /** 风险分 */
    private BigDecimal riskScore;

    /** 周期 */
    private String timeframe;

    /** 触发时间 */
    private LocalDateTime triggerTime;

    /** 数据截止时间 */
    private LocalDateTime dataAsOf;

    /** 数据状态 */
    private String dataStatus;

    /** 证据JSON */
    private String evidenceJson;
}
