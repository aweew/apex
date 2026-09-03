package com.awe.apex.quant.signal.query;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 市场行为计算请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalCalculationReq {

    /** DAY或WEEK */
    @NotBlank(message = "计算周期不能为空")
    private String timeframe;

    /** 可见数据截止时间，为空时取最新完整日线 */
    private LocalDateTime asOfTime;

    /** ALL或SYMBOLS */
    @NotBlank(message = "计算范围不能为空")
    private String scopeType;

    /** 指定证券代码 */
    private List<String> symbols;

    /** SYNC MANUAL BACKFILL REPLAY */
    @NotBlank(message = "触发类型不能为空")
    private String triggerType;
}
