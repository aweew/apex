package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.MarketBreadthForecastResp;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.dto.MorningBriefingResp;
import com.awe.apex.quant.domain.dto.NewsPulseResp;
import com.awe.apex.quant.domain.dto.OvernightMarketQuote;
import com.awe.apex.quant.domain.entity.MarketBreadthForecast;
import com.awe.apex.quant.mapper.MarketBreadthForecastMapper;
import com.awe.apex.quant.market.MarketBreadthForecastEvalReq;
import com.awe.apex.quant.market.MarketBreadthForecastEvalResp;
import com.awe.apex.quant.market.MarketBreadthForecastEvaluator;
import com.awe.apex.quant.market.TradingCalendar;
import com.awe.apex.quant.service.IMarketBreadthForecastService;
import com.awe.apex.quant.service.IMarketBriefingService;
import com.awe.apex.quant.service.IMorningBriefingService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 盘前涨跌比预测与收盘回测服务实现。
 */
@Slf4j
@Service
public class MarketBreadthForecastServiceImpl implements IMarketBreadthForecastService {

    private static final String MODEL_VERSION = "BREADTH_V1";
    private static final int ROLLING_SAMPLE_SIZE = 20;
    private static final int MIN_SETTLEMENT_BREADTH = 1000;
    private static final LocalTime FORECAST_CUTOFF_TIME = LocalTime.of(9, 30);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final MarketBreadthForecastEvaluator evaluator = new MarketBreadthForecastEvaluator();

    @Resource
    private MarketBreadthForecastMapper marketBreadthForecastMapper;

    @Resource
    private IMarketBriefingService marketBriefingService;

    @Resource
    private IMorningBriefingService morningBriefingService;

    /**
     * 获取当前交易日的看板预测；盘前数据已齐全时补建一次预测。
     *
     * @param morningBriefing 盘前晨报
     * @param marketBriefing 昨收市场简报
     * @return 预测与回测信息
     */
    @Override
    public MarketBreadthForecastResp loadForDashboard(MorningBriefingResp morningBriefing,
                                                       MarketBriefingResp marketBriefing) {
        if (Objects.isNull(morningBriefing) || Objects.isNull(morningBriefing.getTradeDate())) {
            return unavailable("盘前晨报未就绪，暂不生成涨跌比预测");
        }
        LocalDate tradeDate = morningBriefing.getTradeDate();
        MarketBreadthForecast forecast = findByTradeDate(tradeDate);
        if (Objects.isNull(forecast) && LocalDate.now().equals(tradeDate)
                && LocalTime.now().isBefore(FORECAST_CUTOFF_TIME)) {
            forecast = createForecast(tradeDate, morningBriefing, marketBriefing);
        }
        if (Objects.isNull(forecast)) {
            return unavailable("盘前预测仅在开盘前固化；等待下一交易日生成");
        }
        return toResp(forecast);
    }

    /**
     * 交易日前生成并固化盘前预测。
     *
     * @return 生成结果说明，成功或已存在时为空字符串
     */
    @Override
    public String generateBeforeOpen() {
        LocalDate tradeDate = LocalDate.now();
        if (!TradingCalendar.isTradingDay(tradeDate)) {
            return "非交易日，跳过盘前涨跌比预测";
        }
        if (!LocalTime.now().isBefore(FORECAST_CUTOFF_TIME)) {
            return "已开盘，拒绝补建盘前涨跌比预测";
        }
        if (Objects.nonNull(findByTradeDate(tradeDate))) {
            return "";
        }
        try {
            MorningBriefingResp morningBriefing = morningBriefingService.generate();
            MarketBriefingResp marketBriefing = marketBriefingService.loadCachedBriefing();
            MarketBreadthForecast forecast = createForecast(tradeDate, morningBriefing, marketBriefing);
            return Objects.isNull(forecast) ? "盘前晨报不完整，未生成涨跌比预测" : "";
        } catch (Exception ex) {
            log.warn("盘前涨跌比预测生成失败，交易日={}，原因={}", tradeDate, ex.getMessage());
            return "盘前涨跌比预测生成失败：" + errorMessage(ex);
        }
    }

    /**
     * 根据收盘后的完整市场广度结算预测回测。
     *
     * @param tradeDate 收盘交易日
     * @return 结算结果说明，成功或无需结算时为空字符串
     */
    @Override
    public String settleAfterClose(LocalDate tradeDate) {
        if (Objects.isNull(tradeDate)) {
            return "盘前涨跌比回测未结算：交易日为空";
        }
        MarketBreadthForecast forecast = findByTradeDate(tradeDate);
        if (Objects.isNull(forecast) || Objects.nonNull(forecast.getSettledAt())) {
            return "";
        }
        try {
            MarketBriefingResp closingBriefing = marketBriefingService.briefing(true);
            if (Objects.isNull(closingBriefing) || !tradeDate.equals(closingBriefing.getAsOf())) {
                return "盘前涨跌比回测未结算：收盘市场简报未覆盖 " + tradeDate;
            }
            Integer actualUpCount = closingBriefing.getBreadthUp();
            Integer actualDownCount = closingBriefing.getBreadthDown();
            if (Objects.isNull(actualUpCount) || Objects.isNull(actualDownCount)) {
                return "盘前涨跌比回测未结算：收盘涨跌家数不完整";
            }
            int breadthTotal = safeCount(actualUpCount) + safeCount(actualDownCount);
            if (breadthTotal < MIN_SETTLEMENT_BREADTH) {
                return "盘前涨跌比回测未结算：收盘广度覆盖不足 " + breadthTotal;
            }

            BigDecimal actualUpRatio = BigDecimal.valueOf(safeCount(actualUpCount)).multiply(HUNDRED)
                    .divide(BigDecimal.valueOf(breadthTotal), 2, RoundingMode.HALF_UP);
            BigDecimal actualDownRatio = HUNDRED.subtract(actualUpRatio).setScale(2, RoundingMode.HALF_UP);
            BigDecimal absoluteError = forecast.getPredictedUpRatio().subtract(actualUpRatio).abs()
                    .setScale(2, RoundingMode.HALF_UP);
            boolean predictedUp = forecast.getPredictedUpRatio().compareTo(new BigDecimal("50")) >= 0;
            boolean actualUp = actualUpRatio.compareTo(new BigDecimal("50")) >= 0;
            LocalDateTime settledAt = LocalDateTime.now();
            forecast.setActualUpCount(actualUpCount);
            forecast.setActualDownCount(actualDownCount);
            forecast.setActualUpRatio(actualUpRatio);
            forecast.setActualDownRatio(actualDownRatio);
            forecast.setAbsoluteError(absoluteError);
            forecast.setDirectionHit(predictedUp == actualUp);
            forecast.setAnalysisSummary(buildAnalysisSummary(forecast, actualUpRatio, absoluteError));
            forecast.setSettledAt(settledAt);
            forecast.setUpdateTime(settledAt);
            marketBreadthForecastMapper.updateById(forecast);
            log.info("盘前涨跌比回测完成，交易日={}，预测上涨占比={}，实际上涨占比={}，绝对误差={}，方向命中={}",
                    tradeDate, forecast.getPredictedUpRatio(), actualUpRatio, absoluteError, forecast.getDirectionHit());
            return "";
        } catch (Exception ex) {
            log.warn("盘前涨跌比回测结算失败，交易日={}，原因={}", tradeDate, ex.getMessage());
            return "盘前涨跌比回测结算失败：" + errorMessage(ex);
        }
    }

    private MarketBreadthForecast createForecast(LocalDate tradeDate, MorningBriefingResp morningBriefing,
                                                  MarketBriefingResp marketBriefing) {
        if (Objects.isNull(morningBriefing) || !tradeDate.equals(morningBriefing.getTradeDate())) {
            return null;
        }
        MarketBreadthForecast existing = findByTradeDate(tradeDate);
        if (Objects.nonNull(existing)) {
            return existing;
        }
        BigDecimal previousBreadthUpRatio = breadthUpRatio(marketBriefing);
        BigDecimal overnightIndexPctChg = averagePctChg(morningBriefing.getIndexQuotes());
        BigDecimal ftseA50PctChg = Objects.nonNull(morningBriefing.getFtseA50Future())
                ? morningBriefing.getFtseA50Future().getPctChg() : null;
        BigDecimal asiaIndexPctChg = averagePctChg(morningBriefing.getAsiaQuotes());
        NewsPulseResp newsPulse = morningBriefing.getNewsPulse();
        BigDecimal calibrationAdjustment = calculateCalibrationAdjustment(tradeDate);
        MarketBreadthForecastEvalResp evaluation = evaluator.evaluate(MarketBreadthForecastEvalReq.builder()
                .previousBreadthUpRatio(previousBreadthUpRatio)
                .overnightIndexPctChg(overnightIndexPctChg)
                .ftseA50PctChg(ftseA50PctChg)
                .asiaIndexPctChg(asiaIndexPctChg)
                .newsBullCount(Objects.nonNull(newsPulse) ? newsPulse.getBullCount() : null)
                .newsBearCount(Objects.nonNull(newsPulse) ? newsPulse.getBearCount() : null)
                .calibrationAdjustment(calibrationAdjustment)
                .build());
        LocalDateTime generatedAt = LocalDateTime.now();
        MarketBreadthForecast forecast = MarketBreadthForecast.builder()
                .tradeDate(tradeDate)
                .generatedAt(generatedAt)
                .modelVersion(MODEL_VERSION)
                .sourceAsOf(Objects.nonNull(morningBriefing.getGeneratedAt())
                        ? morningBriefing.getGeneratedAt() : generatedAt)
                .predictedUpRatio(evaluation.getPredictedUpRatio())
                .predictedDownRatio(evaluation.getPredictedDownRatio())
                .calibrationAdjustment(calibrationAdjustment)
                .confidence(evaluation.getConfidence())
                .factorSummary(String.join("；", evaluation.getReasons()))
                .createTime(generatedAt)
                .updateTime(generatedAt)
                .build();
        try {
            marketBreadthForecastMapper.insert(forecast);
            log.info("盘前涨跌比预测已固化，交易日={}，预测上涨占比={}，置信度={}，依据={}",
                    tradeDate, forecast.getPredictedUpRatio(), forecast.getConfidence(), forecast.getFactorSummary());
            return forecast;
        } catch (Exception ex) {
            MarketBreadthForecast concurrentForecast = findByTradeDate(tradeDate);
            if (Objects.nonNull(concurrentForecast)) {
                return concurrentForecast;
            }
            log.warn("盘前涨跌比预测入库失败，交易日={}，原因={}", tradeDate, ex.getMessage());
            return null;
        }
    }

    private MarketBreadthForecastResp toResp(MarketBreadthForecast forecast) {
        List<String> reasons = new ArrayList<>();
        if (StringUtils.isNotBlank(forecast.getFactorSummary())) {
            for (String reason : forecast.getFactorSummary().split("；")) {
                if (StringUtils.isNotBlank(reason)) {
                    reasons.add(reason);
                }
            }
        }
        boolean settled = Objects.nonNull(forecast.getSettledAt());
        return MarketBreadthForecastResp.builder()
                .available(true)
                .tradeDate(forecast.getTradeDate())
                .generatedAt(forecast.getGeneratedAt())
                .predictedUpRatio(forecast.getPredictedUpRatio())
                .predictedDownRatio(forecast.getPredictedDownRatio())
                .confidence(forecast.getConfidence())
                .reasons(reasons)
                .calibrationAdjustment(forecast.getCalibrationAdjustment())
                .modelVersion(forecast.getModelVersion())
                .settled(settled)
                .actualUpRatio(forecast.getActualUpRatio())
                .actualDownRatio(forecast.getActualDownRatio())
                .absoluteError(forecast.getAbsoluteError())
                .directionHit(forecast.getDirectionHit())
                .analysisSummary(forecast.getAnalysisSummary())
                .rollingBacktestSummary(buildRollingBacktestSummary())
                .message(settled ? "收盘回测已结算" : "收盘后将按实际涨跌家数自动回测")
                .build();
    }

    private MarketBreadthForecastResp unavailable(String message) {
        return MarketBreadthForecastResp.builder()
                .available(false)
                .settled(false)
                .reasons(List.of())
                .message(message)
                .build();
    }

    private MarketBreadthForecast findByTradeDate(LocalDate tradeDate) {
        return marketBreadthForecastMapper.selectOne(Wrappers.<MarketBreadthForecast>lambdaQuery()
                .eq(MarketBreadthForecast::getTradeDate, tradeDate)
                .last("LIMIT 1"));
    }

    private BigDecimal breadthUpRatio(MarketBriefingResp marketBriefing) {
        if (Objects.isNull(marketBriefing) || Objects.isNull(marketBriefing.getBreadthUp())
                || Objects.isNull(marketBriefing.getBreadthDown())) {
            return null;
        }
        int breadthTotal = safeCount(marketBriefing.getBreadthUp()) + safeCount(marketBriefing.getBreadthDown());
        if (breadthTotal < MIN_SETTLEMENT_BREADTH) {
            return null;
        }
        return BigDecimal.valueOf(safeCount(marketBriefing.getBreadthUp())).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(breadthTotal), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal averagePctChg(List<OvernightMarketQuote> quotes) {
        if (CollUtil.isEmpty(quotes)) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (OvernightMarketQuote quote : quotes) {
            if (Objects.nonNull(quote) && Objects.nonNull(quote.getPctChg())) {
                sum = sum.add(quote.getPctChg());
                count++;
            }
        }
        return count > 0 ? sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP) : null;
    }

    private BigDecimal calculateCalibrationAdjustment(LocalDate tradeDate) {
        List<MarketBreadthForecast> settledForecasts = marketBreadthForecastMapper.selectList(
                Wrappers.<MarketBreadthForecast>lambdaQuery()
                        .lt(MarketBreadthForecast::getTradeDate, tradeDate)
                        .isNotNull(MarketBreadthForecast::getActualUpRatio)
                        .orderByDesc(MarketBreadthForecast::getTradeDate)
                        .last("LIMIT " + ROLLING_SAMPLE_SIZE));
        if (CollUtil.isEmpty(settledForecasts)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal residualSum = BigDecimal.ZERO;
        int count = 0;
        for (MarketBreadthForecast settledForecast : settledForecasts) {
            if (Objects.nonNull(settledForecast.getActualUpRatio())
                    && Objects.nonNull(settledForecast.getPredictedUpRatio())) {
                residualSum = residualSum.add(settledForecast.getActualUpRatio()
                        .subtract(settledForecast.getPredictedUpRatio()));
                count++;
            }
        }
        if (count <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return residualSum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                .max(new BigDecimal("-6")).min(new BigDecimal("6"));
    }

    private String buildRollingBacktestSummary() {
        List<MarketBreadthForecast> settledForecasts = marketBreadthForecastMapper.selectList(
                Wrappers.<MarketBreadthForecast>lambdaQuery()
                        .isNotNull(MarketBreadthForecast::getSettledAt)
                        .orderByDesc(MarketBreadthForecast::getTradeDate)
                        .last("LIMIT " + ROLLING_SAMPLE_SIZE));
        if (CollUtil.isEmpty(settledForecasts)) {
            return "尚无已结算样本，收盘后开始累积回测记录";
        }
        int directionHitCount = 0;
        BigDecimal errorSum = BigDecimal.ZERO;
        int errorCount = 0;
        for (MarketBreadthForecast settledForecast : settledForecasts) {
            if (Boolean.TRUE.equals(settledForecast.getDirectionHit())) {
                directionHitCount++;
            }
            if (Objects.nonNull(settledForecast.getAbsoluteError())) {
                errorSum = errorSum.add(settledForecast.getAbsoluteError());
                errorCount++;
            }
        }
        BigDecimal hitRatio = BigDecimal.valueOf(directionHitCount).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(settledForecasts.size()), 0, RoundingMode.HALF_UP);
        BigDecimal averageError = errorCount > 0 ? errorSum.divide(BigDecimal.valueOf(errorCount), 2, RoundingMode.HALF_UP)
                : null;
        String summary = "近 " + settledForecasts.size() + " 次 · 方向命中 " + hitRatio.toPlainString() + "%"
                + (Objects.nonNull(averageError) ? " · 平均偏差 " + averageError.toPlainString() + " 个百分点" : "");
        return settledForecasts.size() < 5 ? summary + " · 样本较少，校准上限为 6 个百分点" : summary + " · 已用于下一次偏差校准";
    }

    private String buildAnalysisSummary(MarketBreadthForecast forecast, BigDecimal actualUpRatio,
                                        BigDecimal absoluteError) {
        boolean directionHit = Boolean.TRUE.equals(forecast.getDirectionHit());
        if (directionHit) {
            return "方向命中：预测上涨 " + forecast.getPredictedUpRatio().toPlainString() + "%；实际上涨 "
                    + actualUpRatio.toPlainString() + "%；偏差 " + absoluteError.toPlainString() + " 个百分点。";
        }
        String bias = forecast.getPredictedUpRatio().compareTo(actualUpRatio) > 0 ? "偏多" : "偏空";
        return "方向未命中：模型" + bias + " " + absoluteError.toPlainString() + " 个百分点。盘前依据为“"
                + forecast.getFactorSummary() + "”，该误差将只作为历史校准样本，不对单日原因做因果归因。";
    }

    private int safeCount(Integer count) {
        return Objects.nonNull(count) && count > 0 ? count : 0;
    }

    private String errorMessage(Exception ex) {
        return StringUtils.isNotBlank(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }
}
