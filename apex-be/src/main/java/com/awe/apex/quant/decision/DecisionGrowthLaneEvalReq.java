package com.awe.apex.quant.decision;

import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.dto.ValuationBriefResp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 科技成长线资格评估入参
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionGrowthLaneEvalReq {

    /** 市场简报 */
    private MarketBriefingResp briefing;

    /** 所属行业 */
    private String industry;

    /** 策略ID */
    private String strategyId;

    /** 共振策略数量 */
    private int confluenceCount;

    /** 热点来源数量 */
    private int hotSourceCount;

    /** 估值与成长质量摘要 */
    private ValuationBriefResp valuation;
}
