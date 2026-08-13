package com.awe.apex.quant.decision;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 决策候选特征
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionFeature {

    /** 证券代码 */
    private String code;

    /** BUY/REDUCE/SELL/HOLD */
    private String action;

    /** 策略ID */
    private String strategyId;

    /** 策略信号分 */
    private BigDecimal signalScore;

    /** 最终评分 */
    private BigDecimal finalScore;

    /** 建议仓位 */
    private BigDecimal suggestedWeight;

    /** 共振策略数 */
    private Integer confluenceCount;

    /** 热点来源数 */
    private Integer hotSourceCount;

    /** 是否匹配主线 */
    private Boolean mainlineMatch;

    /** 估值档位 */
    private String valuationLevel;

    /** 估值分 */
    private BigDecimal valuationScore;

    /** 市场立场 */
    private String marketStance;

    /** 数据质量 */
    private String dataQuality;

    /** 是否可执行 */
    private Boolean executableHint;

    /** 是否被基本面硬门禁拦截 */
    private boolean fundExclude;

    /** 基本面是否偏弱 */
    private boolean fundWeak;

    /** 是否逆主线 */
    private boolean offMainline;

    /** 估值调分 */
    private Integer valuationScoreDelta;

    /** 市场仓位系数 */
    private BigDecimal buyWeightFactor;

    /** 单票仓位上限 */
    private BigDecimal singleLimit;

    /** 是否仅供观察 */
    private boolean observeOnly;

    /** SELECTED/WATCH/REJECTED */
    private String selectionStatus;

    /** 拒绝原因 */
    private String rejectReason;

    /** 全候选排名 */
    private Integer rankNo;

    /** 风险旗标 */
    @Builder.Default
    private List<String> riskFlags = List.of();

    /** 特征哈希 */
    private String featureHash;
}
