package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 报表科目行
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinReportRowResp {

    /**
     * 科目名称
     */
    private String itemName;

    /**
     * 各期数值（与 periods 对齐）
     */
    private List<BigDecimal> values;

    /**
     * 各期原始文本（与 periods 对齐）
     */
    private List<String> texts;
}
