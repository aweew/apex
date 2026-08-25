package com.awe.apex.quant.market;

import cn.hutool.core.collection.CollUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 盘前涨跌比的可解释规则预测器。
 */
public class MarketBreadthForecastEvaluator {

    private static final BigDecimal NEUTRAL_RATIO = new BigDecimal("50");
    private static final BigDecimal MAX_CALIBRATION = new BigDecimal("6");
    private static final BigDecimal MIN_RATIO = new BigDecimal("35");
    private static final BigDecimal MAX_RATIO = new BigDecimal("65");

    /**
     * 根据盘前可用信息计算上涨与下跌占比。
     *
     * @param req 盘前规则输入
     * @return 预测结果
     */
    public MarketBreadthForecastEvalResp evaluate(MarketBreadthForecastEvalReq req) {
        BigDecimal predictedUpRatio = NEUTRAL_RATIO;
        int evidenceCount = 0;
        List<String> reasons = new ArrayList<>();
        if (Objects.isNull(req)) {
            return buildResult(predictedUpRatio, evidenceCount, reasons);
        }

        if (Objects.nonNull(req.getPreviousBreadthUpRatio())) {
            predictedUpRatio = predictedUpRatio.add(req.getPreviousBreadthUpRatio()
                    .subtract(NEUTRAL_RATIO).multiply(new BigDecimal("0.20")));
            evidenceCount++;
            if (req.getPreviousBreadthUpRatio().compareTo(new BigDecimal("53")) >= 0) {
                reasons.add("昨收广度偏强");
            } else if (req.getPreviousBreadthUpRatio().compareTo(new BigDecimal("47")) <= 0) {
                reasons.add("昨收广度偏弱");
            }
        }
        if (Objects.nonNull(req.getOvernightIndexPctChg())) {
            predictedUpRatio = predictedUpRatio.add(req.getOvernightIndexPctChg().multiply(new BigDecimal("3")));
            evidenceCount++;
            addDirectionReason(reasons, req.getOvernightIndexPctChg(), "隔夜指数偏多", "隔夜指数偏空");
        }
        if (Objects.nonNull(req.getFtseA50PctChg())) {
            predictedUpRatio = predictedUpRatio.add(req.getFtseA50PctChg().multiply(new BigDecimal("4")));
            evidenceCount++;
            addDirectionReason(reasons, req.getFtseA50PctChg(), "A50 期指偏强", "A50 期指偏弱");
        }
        if (Objects.nonNull(req.getAsiaIndexPctChg())) {
            predictedUpRatio = predictedUpRatio.add(req.getAsiaIndexPctChg().multiply(new BigDecimal("2")));
            evidenceCount++;
            addDirectionReason(reasons, req.getAsiaIndexPctChg(), "亚太早盘偏多", "亚太早盘偏空");
        }

        Integer newsBullCount = req.getNewsBullCount();
        Integer newsBearCount = req.getNewsBearCount();
        int newsTotal = (Objects.nonNull(newsBullCount) ? newsBullCount : 0)
                + (Objects.nonNull(newsBearCount) ? newsBearCount : 0);
        if (newsTotal > 0) {
            BigDecimal newsBias = BigDecimal.valueOf((Objects.nonNull(newsBullCount) ? newsBullCount : 0)
                            - (Objects.nonNull(newsBearCount) ? newsBearCount : 0))
                    .multiply(new BigDecimal("8"))
                    .divide(BigDecimal.valueOf(newsTotal), 2, RoundingMode.HALF_UP);
            predictedUpRatio = predictedUpRatio.add(newsBias);
            evidenceCount++;
            addDirectionReason(reasons, newsBias, "消息面偏多", "消息面偏空");
        }
        if (Objects.nonNull(req.getCalibrationAdjustment())) {
            BigDecimal calibration = req.getCalibrationAdjustment().max(MAX_CALIBRATION.negate()).min(MAX_CALIBRATION);
            predictedUpRatio = predictedUpRatio.add(calibration);
            if (calibration.compareTo(BigDecimal.ZERO) != 0) {
                reasons.add("已按历史偏差校准");
            }
        }
        return buildResult(predictedUpRatio, evidenceCount, reasons);
    }

    private MarketBreadthForecastEvalResp buildResult(BigDecimal predictedUpRatio, int evidenceCount,
                                                       List<String> reasons) {
        BigDecimal boundedRatio = predictedUpRatio.max(MIN_RATIO).min(MAX_RATIO).setScale(0, RoundingMode.HALF_UP);
        BigDecimal predictedDownRatio = BigDecimal.valueOf(100).subtract(boundedRatio).setScale(2, RoundingMode.HALF_UP);
        BigDecimal upRatio = boundedRatio.setScale(2, RoundingMode.HALF_UP);
        String confidence = "低";
        if (evidenceCount >= 4) {
            confidence = upRatio.subtract(NEUTRAL_RATIO).abs().compareTo(new BigDecimal("15")) >= 0 ? "高" : "中";
        }
        if (CollUtil.isEmpty(reasons)) {
            reasons.add("盘前有效因子不足，按中性基准预测");
        }
        return MarketBreadthForecastEvalResp.builder()
                .predictedUpRatio(upRatio)
                .predictedDownRatio(predictedDownRatio)
                .score(upRatio)
                .confidence(confidence)
                .reasons(reasons)
                .build();
    }

    private void addDirectionReason(List<String> reasons, BigDecimal value, String bullishReason, String bearishReason) {
        if (value.compareTo(new BigDecimal("0.10")) >= 0) {
            reasons.add(bullishReason);
        } else if (value.compareTo(new BigDecimal("-0.10")) <= 0) {
            reasons.add(bearishReason);
        }
    }
}
