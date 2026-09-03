package com.awe.apex.quant.signal.event;

import com.awe.apex.quant.domain.entity.BarDaily;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 基于完整日线的确定性市场行为检测器。
 */
@Component
public class MarketBehaviorDetector {

    private static final String FEATURE_VERSION = "daily-v1";

    /**
     * 在指定可见日期内检测最新完整日线的市场行为。
     *
     * @param symbol 证券代码
     * @param sourceBars 日线数据
     * @param asOfDate 可见数据截止日期
     * @return 市场行为检测结果
     */
    public List<SignalDetectionResult> detect(String symbol, List<BarDaily> sourceBars, LocalDate asOfDate) {
        List<BarDaily> visibleBars = new ArrayList<>();
        if (Objects.nonNull(sourceBars)) {
            for (BarDaily bar : sourceBars) {
                if (Objects.nonNull(bar) && Objects.nonNull(bar.getTradeDate())
                        && !bar.getTradeDate().isAfter(asOfDate)) {
                    visibleBars.add(bar);
                }
            }
        }
        visibleBars.sort(Comparator.comparing(BarDaily::getTradeDate));
        if (visibleBars.size() < 20) {
            return List.of(dataRisk(asOfDate, "可见完整日线不足20根"));
        }

        // 1. 关键字段完整性门控，缺失时禁止方向信号。
        BarDaily latestBar = visibleBars.get(visibleBars.size() - 1);
        if (!complete(latestBar)) {
            return List.of(dataRisk(asOfDate, "最新Bar缺少OHLCV或成交额"));
        }
        int historyStart = Math.max(0, visibleBars.size() - 61);
        List<BarDaily> historyBars = visibleBars.subList(historyStart, visibleBars.size() - 1);
        for (BarDaily historyBar : historyBars) {
            if (!complete(historyBar)) {
                return List.of(dataRisk(asOfDate, "特征窗口存在不完整Bar"));
            }
        }

        // 2. 一次计算共享特征，所有规则使用相同截止时间和口径。
        double close = latestBar.getClosePrice().doubleValue();
        double open = latestBar.getOpenPrice().doubleValue();
        double high = latestBar.getHighPrice().doubleValue();
        double low = latestBar.getLowPrice().doubleValue();
        double range = Math.max(0.000001D, high - low);
        double atr14 = atr(visibleBars, 14);
        double resistance = maximumHigh(historyBars, Math.min(60, historyBars.size()));
        double support = minimumLow(historyBars, Math.min(20, historyBars.size()));
        double ma20 = averageClose(visibleBars, 20, 0);
        double ma60 = averageClose(visibleBars, Math.min(60, visibleBars.size()), 0);
        double previousMa20 = averageClose(visibleBars, 20, 1);
        double volumeAverage20 = averageVolume(historyBars, Math.min(20, historyBars.size()));
        double volumeRatio = latestBar.getVolume().doubleValue() / Math.max(1D, volumeAverage20);
        double closePosition = (close - low) / range;
        double rangeLow60 = minimumLow(historyBars, Math.min(60, historyBars.size()));
        double rangePosition60 = (close - rangeLow60) / Math.max(0.000001D, resistance - rangeLow60);
        double breakoutBuffer = Math.max(0.2D * atr14, resistance * 0.003D);
        double body = Math.abs(close - open);
        double upperShadow = high - Math.max(open, close);
        double latestReturn = (close - visibleBars.get(visibleBars.size() - 2).getClosePrice().doubleValue())
                / visibleBars.get(visibleBars.size() - 2).getClosePrice().doubleValue();
        SignalEvidence evidence = evidence(resistance, support, atr14, volumeRatio, closePosition,
                ma20, ma60, close, rangePosition60, "全部指标仅使用" + asOfDate + "及以前完整日线");
        List<SignalDetectionResult> results = new ArrayList<>();

        // 3. 强势行为。
        boolean breakout = close > resistance + breakoutBuffer;
        if (breakout && volumeRatio >= 1.5D && closePosition >= 0.75D) {
            add(results, "S001", "放量突破", "BULLISH", score(55D + volumeRatio * 12D), 92D, 0D,
                    latestBar.getTradeDate(), evidence);
        }
        if (breakout && volumeRatio <= 1.1D && closePosition >= 0.70D) {
            add(results, "S002", "缩量突破", "BULLISH", score(68D + closePosition * 15D), 78D, 0D,
                    latestBar.getTradeDate(), evidence);
        }
        double platformHigh = maximumHigh(historyBars, Math.min(20, historyBars.size()));
        double platformLow = minimumLow(historyBars, Math.min(20, historyBars.size()));
        if (breakout && platformHigh - platformLow <= 4D * atr14) {
            add(results, "S003", "平台突破", "BULLISH", score(70D + closePosition * 15D), 84D, 0D,
                    latestBar.getTradeDate(), evidence);
        }
        if (low <= resistance + atr14 && low >= resistance - 0.5D * atr14
                && close > resistance && volumeRatio <= 0.9D) {
            add(results, "S004", "突破后回踩不破", "BULLISH", score(72D + closePosition * 12D), 76D, 0D,
                    latestBar.getTradeDate(), evidence);
        }
        if (historyBars.size() >= 5 && close > maximumHigh(historyBars, 5) + 0.1D * atr14
                && volumeRatio >= 1.1D) {
            add(results, "S005", "二次突破", "BULLISH", score(65D + volumeRatio * 10D), 70D, 0D,
                    latestBar.getTradeDate(), evidence);
        }
        if (close > ma20 && ma20 > ma60 && ma20 > previousMa20 && volumeRatio >= 1.2D) {
            add(results, "S006", "趋势加速", "BULLISH", score(60D + (close - ma20) / atr14 * 8D), 82D, 0D,
                    latestBar.getTradeDate(), evidence);
        }

        // 4. 弱势行为。
        int resistanceTouches = resistanceTouches(historyBars, resistance, Math.max(0.5D * atr14, resistance * 0.005D));
        if (resistanceTouches >= 3 && close < resistance && closePosition <= 0.45D) {
            add(results, "W001", "多次突破失败", "BEARISH", score(48D + resistanceTouches * 9D), 78D, 0D,
                    latestBar.getTradeDate(), evidence);
        }
        if (volumeRatio >= 1.5D && body / atr14 <= 0.35D) {
            add(results, "W002", "努力与结果背离", "BEARISH", score(50D + volumeRatio * 12D), 74D, 0D,
                    latestBar.getTradeDate(), evidence);
        }
        if (Math.abs(high - resistance) <= 0.5D * atr14 && volumeRatio >= 1.5D
                && open > close && body >= 0.8D * atr14 && closePosition <= 0.25D) {
            add(results, "W003", "阻力位放量大阴", "BEARISH", score(72D + volumeRatio * 8D), 88D, 0D,
                    latestBar.getTradeDate(), evidence);
        }
        if (rangePosition60 >= 0.8D && volumeRatio >= 1.2D && latestReturn <= 0.005D) {
            add(results, "W004", "高位放量滞涨", "BEARISH", score(55D + volumeRatio * 10D), 72D, 0D,
                    latestBar.getTradeDate(), evidence);
        }
        if (high > resistance && close < resistance && upperShadow >= 0.6D * atr14) {
            add(results, "W005", "冲高回落", "BEARISH", score(64D + upperShadow / atr14 * 10D), 86D, 0D,
                    latestBar.getTradeDate(), evidence);
        }
        if (historyBars.size() >= 5 && previousBreakoutFailed(historyBars, atr14, close)) {
            add(results, "W006", "假突破", "BEARISH", 82D, 82D, 0D,
                    latestBar.getTradeDate(), evidence);
        }

        // 5. 风险行为独立评分，不与方向强度合并。
        if (close - ma20 > 3D * atr14) {
            add(results, "R001", "过度加速", "RISK", 0D, 86D,
                    score(60D + (close - ma20) / atr14 * 8D), latestBar.getTradeDate(), evidence);
        }
        if (close >= resistance && volumeRatio < 0.9D) {
            add(results, "R002", "高位量价背离", "RISK", 0D, 68D, 70D,
                    latestBar.getTradeDate(), evidence);
        }
        if (close < support - 0.3D * atr14) {
            add(results, "R003", "支撑破坏", "RISK", 0D, 88D, 86D,
                    latestBar.getTradeDate(), evidence);
        }
        if (range / close > 0.10D || atr14 / close > 0.06D) {
            add(results, "R004", "波动异常", "RISK", 0D, 76D, 78D,
                    latestBar.getTradeDate(), evidence);
        }
        if (averageAmount(historyBars, Math.min(20, historyBars.size())) < 50_000_000D) {
            add(results, "R005", "流动性不足", "RISK", 0D, 90D, 75D,
                    latestBar.getTradeDate(), evidence);
        }
        return results;
    }

    private boolean complete(BarDaily bar) {
        return Objects.nonNull(bar.getOpenPrice()) && Objects.nonNull(bar.getHighPrice())
                && Objects.nonNull(bar.getLowPrice()) && Objects.nonNull(bar.getClosePrice())
                && Objects.nonNull(bar.getVolume()) && bar.getVolume().signum() > 0
                && Objects.nonNull(bar.getAmount()) && bar.getAmount().signum() > 0;
    }

    private double atr(List<BarDaily> bars, int period) {
        int start = Math.max(1, bars.size() - period);
        double totalRange = 0D;
        int count = 0;
        for (int index = start; index < bars.size(); index++) {
            BarDaily currentBar = bars.get(index);
            double previousClose = bars.get(index - 1).getClosePrice().doubleValue();
            double trueRange = Math.max(currentBar.getHighPrice().doubleValue() - currentBar.getLowPrice().doubleValue(),
                    Math.max(Math.abs(currentBar.getHighPrice().doubleValue() - previousClose),
                            Math.abs(currentBar.getLowPrice().doubleValue() - previousClose)));
            totalRange += trueRange;
            count++;
        }
        return Math.max(0.000001D, totalRange / Math.max(1, count));
    }

    private double averageClose(List<BarDaily> bars, int period, int offset) {
        int end = bars.size() - offset;
        int start = Math.max(0, end - period);
        double total = 0D;
        for (int index = start; index < end; index++) {
            total += bars.get(index).getClosePrice().doubleValue();
        }
        return total / Math.max(1, end - start);
    }

    private double averageVolume(List<BarDaily> bars, int period) {
        int start = Math.max(0, bars.size() - period);
        double total = 0D;
        for (int index = start; index < bars.size(); index++) {
            total += bars.get(index).getVolume().doubleValue();
        }
        return total / Math.max(1, bars.size() - start);
    }

    private double averageAmount(List<BarDaily> bars, int period) {
        int start = Math.max(0, bars.size() - period);
        double total = 0D;
        for (int index = start; index < bars.size(); index++) {
            total += bars.get(index).getAmount().doubleValue();
        }
        return total / Math.max(1, bars.size() - start);
    }

    private double maximumHigh(List<BarDaily> bars, int period) {
        int start = Math.max(0, bars.size() - period);
        double value = Double.NEGATIVE_INFINITY;
        for (int index = start; index < bars.size(); index++) {
            value = Math.max(value, bars.get(index).getHighPrice().doubleValue());
        }
        return value;
    }

    private double minimumLow(List<BarDaily> bars, int period) {
        int start = Math.max(0, bars.size() - period);
        double value = Double.POSITIVE_INFINITY;
        for (int index = start; index < bars.size(); index++) {
            value = Math.min(value, bars.get(index).getLowPrice().doubleValue());
        }
        return value;
    }

    private int resistanceTouches(List<BarDaily> bars, double resistance, double radius) {
        int touches = 0;
        int lastTouchIndex = -3;
        for (int index = 0; index < bars.size(); index++) {
            if (Math.abs(bars.get(index).getHighPrice().doubleValue() - resistance) <= radius
                    && index - lastTouchIndex >= 3) {
                touches++;
                lastTouchIndex = index;
            }
        }
        return touches;
    }

    private boolean previousBreakoutFailed(List<BarDaily> historyBars, double atr14, double close) {
        int start = Math.max(20, historyBars.size() - 5);
        for (int index = start; index < historyBars.size(); index++) {
            List<BarDaily> earlierBars = historyBars.subList(0, index);
            double earlierResistance = maximumHigh(earlierBars, Math.min(60, earlierBars.size()));
            if (historyBars.get(index).getClosePrice().doubleValue() > earlierResistance + 0.2D * atr14
                    && close < earlierResistance - 0.2D * atr14) {
                return true;
            }
        }
        return false;
    }

    private SignalDetectionResult dataRisk(LocalDate asOfDate, String reason) {
        SignalEvidence evidence = SignalEvidence.builder().reason(reason).build();
        return SignalDetectionResult.builder()
                .signalCode("R007")
                .signalName("数据风险")
                .direction("RISK")
                .lifecycleState("TRIGGERED")
                .strength(BigDecimal.ZERO)
                .confidence(BigDecimal.valueOf(20))
                .riskScore(BigDecimal.valueOf(100))
                .asOfTime(asOfDate.atTime(15, 0))
                .dataStatus("PARTIAL")
                .featureVersion(FEATURE_VERSION)
                .evidence(evidence)
                .build();
    }

    private SignalEvidence evidence(double resistance, double support, double atr14, double volumeRatio,
                                    double closePosition, double ma20, double ma60, double close,
                                    double rangePosition60, String reason) {
        return SignalEvidence.builder()
                .resistancePrice(decimal(resistance))
                .supportPrice(decimal(support))
                .atr14(decimal(atr14))
                .volumeRatio(decimal(volumeRatio))
                .closePosition(decimal(closePosition))
                .ma20(decimal(ma20))
                .ma60(decimal(ma60))
                .closePrice(decimal(close))
                .rangePosition60(decimal(rangePosition60))
                .reason(reason)
                .build();
    }

    private void add(List<SignalDetectionResult> results, String code, String name, String direction,
                     double strength, double confidence, double riskScore, LocalDate tradeDate,
                     SignalEvidence evidence) {
        results.add(SignalDetectionResult.builder()
                .signalCode(code)
                .signalName(name)
                .direction(direction)
                .lifecycleState("TRIGGERED")
                .strength(decimal(strength))
                .confidence(decimal(confidence))
                .riskScore(decimal(riskScore))
                .asOfTime(tradeDate.atTime(15, 0))
                .dataStatus("COMPLETE")
                .featureVersion(FEATURE_VERSION)
                .evidence(evidence)
                .build());
    }

    private double score(double value) {
        return Math.max(0D, Math.min(100D, value));
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }
}
