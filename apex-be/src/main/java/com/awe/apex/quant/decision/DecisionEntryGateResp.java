package com.awe.apex.quant.decision;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 开仓资格门禁结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionEntryGateResp {

    /**
     * 是否允许进入组合风险预算
     */
    private boolean passed;

    /**
     * 拦截原因
     */
    @Builder.Default
    private List<String> blockReasons = new ArrayList<>();
}
