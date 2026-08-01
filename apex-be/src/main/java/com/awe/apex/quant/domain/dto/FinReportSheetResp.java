package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 单张报表透视（科目 × 报告期）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinReportSheetResp {

    /**
     * 报表类型 profit/balance/cashflow
     */
    private String statementType;

    /**
     * 报表中文名
     */
    private String statementName;

    /**
     * 报告期列（新→旧）
     */
    private List<LocalDate> periods;

    /**
     * 科目行
     */
    private List<FinReportRowResp> rows;
}
