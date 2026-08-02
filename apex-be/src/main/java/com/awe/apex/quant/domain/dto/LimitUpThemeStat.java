package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 涨停题材统计
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LimitUpThemeStat {

    /**
     * 题材名
     */
    private String theme;

    /**
     * 涨停家数
     */
    private Integer count;

    /**
     * 最高连板
     */
    private Integer maxLianban;
}
