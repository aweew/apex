package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 板块榜单响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectorBoardResp {

    /**
     * 板块类型
     */
    private String boardType;

    /**
     * 排序字段 pctChg/netInflow
     */
    private String sortBy;

    /**
     * 排序方向 asc/desc
     */
    private String order;

    /**
     * 交易日
     */
    private LocalDate tradeDate;

    /**
     * 本地已有数据的交易日列表（近 N 日，降序）
     */
    private List<LocalDate> availableDates;

    /**
     * 同步时间
     */
    private LocalDateTime syncedAt;

    /**
     * 榜单
     */
    private List<SectorBoardItem> items;

    /**
     * 说明
     */
    private String message;

    /**
     * 资金单位说明
     */
    private String inflowUnit;
}
