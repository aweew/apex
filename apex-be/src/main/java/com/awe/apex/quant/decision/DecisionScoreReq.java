package com.awe.apex.quant.decision;

import com.awe.apex.quant.domain.dto.ValuationBriefResp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 决策评分入参
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionScoreReq {

    /**
     * 策略基分
     */
    private BigDecimal signalScore;

    /**
     * 策略 ID（S1/S2/S3）
     */
    private String strategyId;

    /**
     * 共振策略数
     */
    private int confluenceCount;

    /**
     * 热点源数量，无热点为 0
     */
    private int hotSourceCount;

    /**
     * 基本面硬剔除
     */
    private boolean fundExclude;

    /**
     * 基本面偏弱
     */
    private boolean fundWeak;

    /**
     * 命中主线
     */
    private boolean mainlineMatch;

    /**
     * 有主线但行业逆主线
     */
    private boolean offMainline;

    /** 是否进入科技成长线 */
    private boolean growthLane;

    /**
     * 估值摘要
     */
    private ValuationBriefResp valuation;

    /**
     * 市场立场：进攻/均衡/防守
     */
    private String marketStance;

    /**
     * 市场仓位因子
     */
    private BigDecimal buyWeightFactor;

    /**
     * 单票仓位上限
     */
    private BigDecimal singleLimit;

    /**
     * 观察池候选（基本面剔除后的降权分支）
     */
    private boolean observeOnly;
}
