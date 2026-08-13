package com.awe.apex.quant.decision;

import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 决策特征来源
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionFeatureSource {

    /** 评分原始输入 */
    private DecisionFeatureInput scoringInput;

    /** 热点来源数 */
    private int hotSourceCount;

    /** 市场简报 */
    private MarketBriefingResp briefing;

    /** SELECTED/WATCH/REJECTED */
    private String selectionStatus;

    /** 拒绝原因 */
    private String rejectReason;

    /** 全候选排名 */
    private Integer rankNo;
}
