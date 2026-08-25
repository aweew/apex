package com.awe.apex.quant.decision;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 科技成长线资格评估结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionGrowthLaneEvalResp {

    /** 是否符合科技成长线资格 */
    private boolean qualified;

    /** 是否属于科技行业 */
    private boolean technologyIndustry;

    /** 未通过资格的具体原因 */
    @Builder.Default
    private List<String> rejectReasons = new ArrayList<>();
}
