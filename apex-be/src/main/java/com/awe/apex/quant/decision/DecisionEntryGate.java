package com.awe.apex.quant.decision;

import com.awe.apex.quant.strategy.StrategyParams;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 买入候选的硬门禁：市场可开仓与板块确认
 */
@Component
public class DecisionEntryGate {

    @Resource
    private StrategyParams strategyParams;

    /**
     * 校验候选是否可进入组合风险预算
     *
     * @param req 门禁入参
     * @return 门禁结果
     */
    public DecisionEntryGateResp evaluate(DecisionEntryGateReq req) {
        List<String> blockReasons = new ArrayList<>();
        if (Objects.isNull(req)) {
            blockReasons.add("开仓门禁入参缺失，禁止新开仓");
            return DecisionEntryGateResp.builder().passed(false).blockReasons(blockReasons).build();
        }

        if (!req.isDataSufficient()) {
            blockReasons.add("市场数据不足，禁止新开仓");
        }
        Integer breadthUp = req.getBreadthUp();
        if (Objects.isNull(breadthUp) || breadthUp < strategyParams.decisionGateMinimumBreadthUp()) {
            blockReasons.add("市场广度不足，禁止新开仓");
        }
        if (req.isOffMainline() && !req.isGrowthLane()) {
            blockReasons.add("行业逆主线，禁止新开仓");
        } else if (!req.isMainlineMatch()
                && req.getHotSourceCount() < strategyParams.decisionGateMinimumHotSources()) {
            blockReasons.add("缺少主线或多源热点确认，仅观察");
        }
        return DecisionEntryGateResp.builder()
                .passed(blockReasons.isEmpty())
                .blockReasons(blockReasons)
                .build();
    }
}
