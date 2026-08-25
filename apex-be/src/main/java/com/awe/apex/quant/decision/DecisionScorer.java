package com.awe.apex.quant.decision;

import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.ValuationBriefResp;
import com.awe.apex.quant.strategy.StrategyParams;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 统一决策评分：策略基分 + 共振/热点/主线/基本面/估值/市场立场 + 估值×策略联动
 */
@Component
public class DecisionScorer {

    private static final BigDecimal BASE_WEIGHT = new BigDecimal("0.10");
    private static final BigDecimal CONFLUENCE_WEIGHT = new BigDecimal("0.12");

    @Resource
    private StrategyParams strategyParams;

    /**
     * 计算买入/观察综合分与仓位建议
     *
     * @param req 入参
     * @return 评分结果
     */
    public DecisionScoreResp scoreBuy(DecisionScoreReq req) {
        if (Objects.isNull(req)) {
            return DecisionScoreResp.builder()
                    .finalScore(new BigDecimal("60"))
                    .suggestedWeight(BigDecimal.ZERO)
                    .scoreExplain("无评分入参")
                    .riskFlags(List.of())
                    .executableHint(false)
                    .build();
        }

        BigDecimal score = Objects.nonNull(req.getSignalScore()) ? req.getSignalScore() : new BigDecimal("60");
        StringBuilder explain = new StringBuilder();
        explain.append("策略").append(nullToDash(req.getStrategyId()))
                .append(" 基分").append(score.setScale(0, RoundingMode.HALF_UP));

        List<String> riskFlags = new ArrayList<>();
        String linkHint = null;
        boolean blockExecutable = false;
        boolean growthLane = req.isGrowthLane();

        int minCf = strategyParams.decisionConfluenceMinStrategies();
        if (req.getConfluenceCount() >= minCf) {
            BigDecimal boost = strategyParams.decisionScoreConfluence();
            score = score.add(boost);
            explain.append(" · 共振+").append(plain(boost));
        }

        int hotCnt = req.getHotSourceCount();
        if (hotCnt >= 2) {
            BigDecimal hotBoost = strategyParams.decisionScoreHot();
            score = score.add(hotBoost);
            if (hotCnt >= 3) {
                BigDecimal triple = strategyParams.decisionScoreHotTriple();
                score = score.add(triple);
                explain.append(" · 热点+").append(plain(hotBoost.add(triple)));
            } else {
                explain.append(" · 热点+").append(plain(hotBoost));
            }
        }

        if (req.isFundWeak()) {
            BigDecimal penalty = strategyParams.decisionScoreFundPenalty();
            score = score.subtract(penalty);
            explain.append(" · 基本面-").append(plain(penalty));
            riskFlags.add("基本面偏弱");
        }

        if (req.isMainlineMatch()) {
            BigDecimal boost = strategyParams.decisionScoreMainline();
            score = score.add(boost);
            explain.append(" · 主线+").append(plain(boost));
        } else if (req.isOffMainline() && !growthLane) {
            BigDecimal penalty = strategyParams.decisionScoreOffMainline();
            score = score.subtract(penalty);
            explain.append(" · 逆主线-").append(plain(penalty));
            riskFlags.add("逆主线");
        } else if (req.isOffMainline()) {
            explain.append(" · 成长线非主线受控");
            riskFlags.add("成长线非主线受控");
        }

        ValuationBriefResp val = req.getValuation();
        String level = Objects.nonNull(val) ? val.getLevel() : null;
        boolean cheap = "UNDERVALUED".equals(level) || "SLIGHTLY_CHEAP".equals(level);
        boolean rich = "OVERVALUED".equals(level) || "SLIGHTLY_EXPENSIVE".equals(level);
        if (Objects.nonNull(val) && StringUtils.isNotBlank(val.getLevel()) && !"UNKNOWN".equals(val.getLevel())) {
            int valuationScoreDelta = val.getScoreDelta();
            if (growthLane && rich) {
                valuationScoreDelta = Math.max(valuationScoreDelta, -4);
            }
            if (valuationScoreDelta != 0) {
                score = score.add(BigDecimal.valueOf(valuationScoreDelta));
                explain.append(" · 估值").append(val.getLevelLabel())
                        .append(valuationScoreDelta > 0 ? "+" : "")
                        .append(valuationScoreDelta);
            } else if (StringUtils.isNotBlank(val.getLevelLabel())) {
                explain.append(" · 估值").append(val.getLevelLabel());
            }
        }

        // 估值 × 策略联动
        String sid = StringUtils.isNotBlank(req.getStrategyId()) ? req.getStrategyId().toUpperCase() : "";
        if (cheap && "S2".equals(sid)) {
            BigDecimal link = strategyParams.decisionLinkUndervaluedS2();
            score = score.add(link);
            explain.append(" · 低估回调+").append(plain(link));
            linkHint = "低估回调优先";
        } else if (rich && "S3".equals(sid) && !growthLane) {
            BigDecimal link = strategyParams.decisionLinkOvervaluedS3();
            score = score.subtract(link);
            explain.append(" · 高估突破-").append(plain(link));
            linkHint = "高估突破降权";
            riskFlags.add("高估突破降权");
            blockExecutable = true;
        } else if (rich) {
            riskFlags.add(growthLane ? "成长溢价受限" : "估值偏高");
            if (growthLane && "S3".equals(sid)) {
                linkHint = "成长突破受限仓位";
                explain.append(" · 成长突破受限仓位");
            }
        }

        String stance = req.getMarketStance();
        if ("防守".equals(stance)) {
            BigDecimal penalty = strategyParams.decisionScoreDefense();
            score = score.subtract(penalty);
            explain.append(" · 防守-").append(plain(penalty));
        } else if ("进攻".equals(stance) && req.getConfluenceCount() >= minCf) {
            BigDecimal boost = strategyParams.decisionScoreOffense();
            score = score.add(boost);
            explain.append(" · 进攻共振+").append(plain(boost));
        }

        if (req.isObserveOnly() || req.isFundExclude()) {
            score = score.subtract(new BigDecimal("10")).max(new BigDecimal("45"));
            explain.append(" · 观察候选");
            riskFlags.add("基本面未过买入门槛");
            blockExecutable = true;
        }

        BigDecimal singleLimit = Objects.nonNull(req.getSingleLimit())
                ? req.getSingleLimit() : new BigDecimal("0.15");
        if (growthLane) {
            singleLimit = singleLimit.min(strategyParams.decisionGrowthSingleLimit());
        }
        BigDecimal buyFactor = Objects.nonNull(req.getBuyWeightFactor())
                ? req.getBuyWeightFactor() : BigDecimal.ONE;
        boolean confluenceOk = req.getConfluenceCount() >= minCf || hotCnt >= 2 || req.isMainlineMatch();
        BigDecimal weight = BigDecimal.ZERO;
        if (!req.isObserveOnly() && !req.isFundExclude()) {
            weight = suggestWeight(confluenceOk, !req.isFundWeak(), singleLimit);
            if (req.isMainlineMatch()) {
                weight = weight.multiply(new BigDecimal("1.08"));
            } else if (req.isOffMainline()) {
                weight = weight.multiply(new BigDecimal("0.85"));
            }
            if (Objects.nonNull(val)) {
                if ("OVERVALUED".equals(val.getLevel())) {
                    weight = weight.multiply(new BigDecimal("0.70"));
                } else if ("SLIGHTLY_EXPENSIVE".equals(val.getLevel())) {
                    weight = weight.multiply(new BigDecimal("0.85"));
                } else if ("UNDERVALUED".equals(val.getLevel())) {
                    weight = weight.multiply(new BigDecimal("1.08"));
                }
            }
            weight = weight.multiply(buyFactor).min(singleLimit).setScale(4, RoundingMode.HALF_UP);
        }

        explain.append(" · 仓位").append(pctText(weight));
        if (buyFactor.compareTo(BigDecimal.ONE) != 0) {
            explain.append("（市场×").append(buyFactor.setScale(2, RoundingMode.HALF_UP)).append("）");
        }
        if (StringUtils.isNotBlank(linkHint)) {
            explain.append(" · ").append(linkHint);
        }

        BigDecimal executableFloor = growthLane
                ? strategyParams.decisionGrowthExecutableScore() : strategyParams.decisionExecutableScore();
        boolean executableHint = !blockExecutable
                && !req.isFundExclude()
                && !req.isObserveOnly()
                && score.compareTo(executableFloor) >= 0
                && !(rich && "S3".equals(sid) && !growthLane);

        String explainText = explain.toString();
        if (explainText.length() > 500) {
            explainText = explainText.substring(0, 500);
        }

        return DecisionScoreResp.builder()
                .finalScore(score.setScale(2, RoundingMode.HALF_UP))
                .suggestedWeight(weight)
                .scoreExplain(explainText)
                .riskFlags(riskFlags)
                .executableHint(executableHint)
                .linkHint(linkHint)
                .build();
    }

    /**
     * 卖出侧轻量加分（共振/热点）
     *
     * @param signalScore      策略分
     * @param confluenceCount  共振数
     * @param hotSourceCount   热点源数
     * @return 综合分
     */
    public BigDecimal scoreSell(BigDecimal signalScore, int confluenceCount, int hotSourceCount) {
        BigDecimal score = Objects.nonNull(signalScore) ? signalScore : new BigDecimal("60");
        int minCf = strategyParams.decisionConfluenceMinStrategies();
        if (confluenceCount >= minCf) {
            score = score.add(strategyParams.decisionScoreConfluence());
        }
        if (hotSourceCount >= 2) {
            score = score.add(strategyParams.decisionScoreHot());
            if (hotSourceCount >= 3) {
                score = score.add(strategyParams.decisionScoreHotTriple());
            }
        }
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal suggestWeight(boolean confluence, boolean fundOk, BigDecimal singleLimit) {
        BigDecimal weight = BASE_WEIGHT;
        if (confluence && fundOk) {
            weight = singleLimit.min(new BigDecimal("0.15"));
        } else if (confluence) {
            weight = CONFLUENCE_WEIGHT;
        }
        return weight.min(singleLimit).setScale(4, RoundingMode.HALF_UP);
    }

    private static String plain(BigDecimal v) {
        return Objects.nonNull(v) ? v.stripTrailingZeros().toPlainString() : "0";
    }

    private static String nullToDash(String s) {
        return StringUtils.isNotBlank(s) ? s : "-";
    }

    private static String pctText(BigDecimal ratio) {
        if (Objects.isNull(ratio)) {
            return "-";
        }
        return ratio.multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_UP) + "%";
    }
}
