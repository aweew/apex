package com.awe.apex.quant.signal.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 市场行为事件写入对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalEventWriteBO {

    /** 事件ID */
    private Long id;

    /** 事件编号 */
    private String eventNo;

    /** 定义ID */
    private Long definitionId;

    /** 规则ID */
    private Long ruleId;

    /** 计算批次ID */
    private Long calculationRunId;

    /** 证券代码 */
    private String symbol;

    /** 周期 */
    private String timeframe;

    /** 触发时间 */
    private LocalDateTime triggerTime;

    /** 数据截止时间 */
    private LocalDateTime asOfTime;

    /** 信号方向 */
    private String direction;

    /** 生命周期状态 */
    private String lifecycleState;

    /** 行为强度 */
    private BigDecimal strength;

    /** 置信度 */
    private BigDecimal confidence;

    /** 风险分 */
    private BigDecimal riskScore;

    /** 有效截止时间 */
    private LocalDateTime validUntil;

    /** 证据JSON */
    private String evidenceJson;

    /** 数据状态 */
    private String dataStatus;

    /** 特征版本 */
    private String featureVersion;
}
