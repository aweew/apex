package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 某日板块轮动 Top
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectorRotationDay {

    /**
     * 交易日
     */
    private LocalDate tradeDate;

    /**
     * Top 名称列表（带涨跌幅文案）
     */
    private List<String> tops;
}
