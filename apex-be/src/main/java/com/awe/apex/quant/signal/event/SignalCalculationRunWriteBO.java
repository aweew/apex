package com.awe.apex.quant.signal.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 信号计算批次写入对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalCalculationRunWriteBO {

    /** 批次主键 */
    private Long id;

    /** 批次号 */
    private String runNo;

    /** 触发类型 */
    private String triggerType;

    /** 周期 */
    private String timeframe;

    /** 数据截止时间 */
    private LocalDateTime asOfTime;

    /** 计划证券数 */
    private Integer totalCount;
}
