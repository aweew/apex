package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 板块成分股响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectorConstituentResp {

    /**
     * 板块代码
     */
    private String sectorCode;

    /**
     * 板块名称
     */
    private String sectorName;

    /**
     * 板块类型
     */
    private String boardType;

    /**
     * 交易日
     */
    private LocalDate tradeDate;

    /**
     * 同步时间
     */
    private LocalDateTime syncedAt;

    /**
     * 成分股列表
     */
    private List<SectorConstituentItem> items;

    /**
     * 说明
     */
    private String message;
}
