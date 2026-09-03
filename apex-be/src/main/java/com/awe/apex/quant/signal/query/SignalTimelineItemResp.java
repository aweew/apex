package com.awe.apex.quant.signal.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 信号生命周期时间轴项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalTimelineItemResp {

    /** 事件ID */
    private Long eventId;

    /** 信号编码 */
    private String signalCode;

    /** 信号名称 */
    private String signalName;

    /** 迁移前状态 */
    private String fromState;

    /** 迁移后状态 */
    private String toState;

    /** 迁移原因编码 */
    private String reasonCode;

    /** 迁移时间 */
    private LocalDateTime eventTime;

    /** 数据截止时间 */
    private LocalDateTime dataAsOf;

    /** 证据JSON */
    private String evidenceJson;
}
