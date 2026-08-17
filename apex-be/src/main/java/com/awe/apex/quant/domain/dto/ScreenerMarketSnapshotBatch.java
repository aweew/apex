package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 全市场实时选股截面批次
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenerMarketSnapshotBatch {

    /** 数据来源 */
    private String source;

    /** 数据截止时间 */
    private LocalDateTime asOf;

    /** 股票截面 */
    private List<ScreenerMarketSnapshot> items;
}
