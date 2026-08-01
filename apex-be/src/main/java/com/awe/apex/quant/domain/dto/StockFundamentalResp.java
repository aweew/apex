package com.awe.apex.quant.domain.dto;

import com.awe.apex.quant.domain.entity.StockFinAbstract;
import com.awe.apex.quant.domain.entity.StockFinIndicator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 个股基本面汇总
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockFundamentalResp {

    /**
     * 证券代码
     */
    private String code;

    /**
     * 最新财务摘要（KPI）
     */
    private StockFinAbstract latestAbstract;

    /**
     * 财务摘要历史（新→旧）
     */
    private List<StockFinAbstract> abstracts;

    /**
     * 最新分析指标
     */
    private StockFinIndicator latestIndicator;

    /**
     * 分析指标历史（新→旧）
     */
    private List<StockFinIndicator> indicators;

    /**
     * 利润表
     */
    private FinReportSheetResp profitSheet;

    /**
     * 资产负债表
     */
    private FinReportSheetResp balanceSheet;

    /**
     * 现金流量表
     */
    private FinReportSheetResp cashflowSheet;

    /**
     * 摘要条数
     */
    private Integer abstractCount;

    /**
     * 指标条数
     */
    private Integer indicatorCount;

    /**
     * 报表科目条数
     */
    private Integer reportItemCount;

    /**
     * 说明
     */
    private String note;
}
