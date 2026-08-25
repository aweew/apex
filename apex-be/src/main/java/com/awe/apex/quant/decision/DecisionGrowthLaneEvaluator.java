package com.awe.apex.quant.decision;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.MarketFactorItem;
import com.awe.apex.quant.domain.dto.ValuationBriefResp;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 科技成长线资格评估器。
 */
@Component
public class DecisionGrowthLaneEvaluator {

    private static final List<String> TECHNOLOGY_INDUSTRY_KEYWORDS = List.of(
            "半导体", "电子", "软件", "通信", "计算机", "互联网", "元件", "光学", "自动化");

    /**
     * 判断候选能否进入科技成长线。
     *
     * @param req 资格评估入参
     * @return 资格评估结果
     */
    public DecisionGrowthLaneEvalResp evaluate(DecisionGrowthLaneEvalReq req) {
        List<String> rejectReasons = new ArrayList<>();
        boolean technologyIndustry = Objects.nonNull(req) && isTechnologyIndustry(req.getIndustry());
        if (Objects.isNull(req) || !isGrowthStyleConfirmed(req)) {
            rejectReasons.add("成长风格未确认");
        }
        if (!technologyIndustry) {
            rejectReasons.add("非科技行业");
        }
        if (Objects.isNull(req) || !isGrowthQualityVerified(req.getValuation())) {
            rejectReasons.add("增长质量未验证");
        }
        if (Objects.isNull(req) || req.getHotSourceCount() < 2) {
            rejectReasons.add("个股热点确认不足");
        }
        if (Objects.isNull(req) || !hasBreakoutOrConfluence(req)) {
            rejectReasons.add("缺少突破或多策略共振");
        }
        return DecisionGrowthLaneEvalResp.builder()
                .qualified(CollUtil.isEmpty(rejectReasons))
                .technologyIndustry(technologyIndustry)
                .rejectReasons(rejectReasons)
                .build();
    }

    private boolean isGrowthStyleConfirmed(DecisionGrowthLaneEvalReq req) {
        if (Objects.isNull(req.getBriefing()) || CollUtil.isEmpty(req.getBriefing().getFactors())) {
            return false;
        }
        for (MarketFactorItem factor : req.getBriefing().getFactors()) {
            if (Objects.nonNull(factor)
                    && "最近风格".equals(factor.getName())
                    && "偏多".equals(factor.getSignal())
                    && StringUtils.contains(factor.getValue(), "成长/小盘占优")) {
                return true;
            }
        }
        return false;
    }

    private boolean isTechnologyIndustry(String industry) {
        if (StringUtils.isBlank(industry)) {
            return false;
        }
        for (String keyword : TECHNOLOGY_INDUSTRY_KEYWORDS) {
            if (StringUtils.contains(industry, keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isGrowthQualityVerified(ValuationBriefResp valuation) {
        return Objects.nonNull(valuation) && Boolean.TRUE.equals(valuation.getGrowthQualityVerified());
    }

    private boolean hasBreakoutOrConfluence(DecisionGrowthLaneEvalReq req) {
        return "S3".equalsIgnoreCase(req.getStrategyId()) || req.getConfluenceCount() >= 2;
    }
}
