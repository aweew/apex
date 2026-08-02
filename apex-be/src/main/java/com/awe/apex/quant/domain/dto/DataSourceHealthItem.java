package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 数据源健康项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceHealthItem {

    /**
     * 源名称
     */
    private String name;

    /**
     * GREEN/YELLOW/RED
     */
    private String level;

    /**
     * 数据日期
     */
    private LocalDate dataAsOf;

    /**
     * 最近同步时间
     */
    private LocalDateTime syncedAt;

    /**
     * 说明
     */
    private String note;
}
