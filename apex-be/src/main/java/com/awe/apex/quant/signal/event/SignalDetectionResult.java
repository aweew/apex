package com.awe.apex.quant.signal.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 单个市场行为检测结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalDetectionResult {

    /** 信号编码 */
    private String signalCode;

    /** 信号名称 */
    private String signalName;

    /** BULLISH BEARISH RISK */
    private String direction;

    /** 生命周期状态 */
    private String lifecycleState;

    /** 行为强度0到100 */
    private BigDecimal strength;

    /** 数据与规则置信度0到100 */
    private BigDecimal confidence;

    /** 独立风险分0到100 */
    private BigDecimal riskScore;

    /** 可见数据截止时间 */
    private LocalDateTime asOfTime;

    /** 数据完整状态 */
    private String dataStatus;

    /** 特征版本 */
    private String featureVersion;

    /** 量化证据 */
    private SignalEvidence evidence;
}
