package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 周几盈亏分布
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeekdayPnlResp {

    /**
     * 样本数
     */
    private Integer sampleCount;

    /**
     * 说明
     */
    private String message;

    /**
     * 分日
     */
    private List<WeekdayPnlItem> items;
}
