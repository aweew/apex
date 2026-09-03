package com.awe.apex.quant.signal.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 信号中心概览响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalOverviewResp {

    /** 强势信号数量 */
    private Long bullishCount;

    /** 弱势信号数量 */
    private Long bearishCount;

    /** 风险信号数量 */
    private Long riskCount;

    /** 已确认或活跃数量 */
    private Long confirmedCount;

    /** 数据截止时间 */
    private LocalDateTime dataAsOf;

    /** 数据状态 */
    private String dataStatus;

    /** 特征版本 */
    private String featureVersion;
}
