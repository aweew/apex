package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 个股一页综合研判
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAnalysisResp {

    /**
     * 证券代码
     */
    private String code;

    /**
     * 证券简称
     */
    private String name;

    /**
     * 最新价
     */
    private BigDecimal latestPrice;

    /**
     * 涨跌幅%
     */
    private BigDecimal pctChg;

    /**
     * 行业
     */
    private String industry;

    /**
     * 综合分 0~100
     */
    private BigDecimal compositeScore;

    /**
     * 立场：积极关注 / 可跟踪 / 中性观望 / 谨慎 / 回避
     */
    private String stance;

    /**
     * 结论摘要
     */
    private String summary;

    /**
     * 行动建议
     */
    private String actionHint;

    /**
     * 多头要点
     */
    private List<String> bullPoints;

    /**
     * 空头/风险要点
     */
    private List<String> bearPoints;

    /**
     * 风险标签
     */
    private List<String> riskFlags;

    /**
     * 维度得分说明
     */
    private List<String> scoreExplain;

    /**
     * 技术面
     */
    private StockAnalysisTechResp tech;

    /**
     * 估值（完整）
     */
    private ValuationResp valuation;

    /**
     * 资金与情绪
     */
    private StockAnalysisCapitalResp capital;

    /**
     * 当日策略信号
     */
    private List<SignalItemResp> signals;

    /**
     * 今日决策条目（若有）
     */
    private DecisionItemResp decision;

    /**
     * 数据说明
     */
    private String dataNote;
}
