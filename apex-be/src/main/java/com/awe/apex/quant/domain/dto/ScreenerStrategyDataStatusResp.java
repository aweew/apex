package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 选股数据时点与质量状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenerStrategyDataStatusResp {

    /** 策略运行时间 */
    private LocalDateTime runAt;

    /** 实时截面截止时间 */
    private LocalDateTime snapshotAsOf;

    /** 日线截止交易日 */
    private LocalDate dailyAsOf;

    /** 分时截止分钟 */
    private String intradayAsOf;

    /** 截面股票数量 */
    private Integer snapshotCount;

    /** 分时复核候选数量 */
    private Integer intradayCandidateCount;

    /** 分时复核成功数量 */
    private Integer intradayReviewedCount;

    /** 是否降级 */
    private Boolean degraded;

    /** 数据缺失或失败摘要 */
    private List<ScreenerDataIssueResp> issues;

    /** 运行说明 */
    private List<String> notes;
}
