package com.awe.apex.quant.domain.dto;

import com.awe.apex.quant.decision.DecisionMode;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 一键决策请求
 */
@Data
public class DecisionRunReq {

    /**
     * 自选分组，默认「我的自选」
     */
    private String groupName;

    /**
     * 决策日，可空=今天
     */
    private LocalDate date;

    /**
     * 运行模式，可空；过去日期默认按 REPLAY 处理
     */
    private DecisionMode mode;

    /**
     * 数据可见截止时间；REPLAY 可空，默认决策日末
     */
    private LocalDateTime asOfTime;
}
