package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 今日操作指引。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TodayOperationGuideResp {

    /**
     * 操作顺序总述。
     */
    private String summary;

    /**
     * 目标总仓位下限。
     */
    private BigDecimal targetPositionMin;

    /**
     * 目标总仓位上限。
     */
    private BigDecimal targetPositionMax;

    /**
     * 新建仓仓位系数。
     */
    private BigDecimal newPositionFactor;

    /**
     * 整体阻断原因。
     */
    private String blockedReason;

    /**
     * 按优先级排序的操作项，最多三项。
     */
    private List<OperationGuideItemResp> items;
}
