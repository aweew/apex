package com.awe.apex.quant.indicator;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.quant.domain.dto.ObserveTechSignal;
import com.awe.apex.quant.domain.dto.TechRegimeResult;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.strategy.BarSeries;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 技术结构状态机 + RS 微调（雷达仅附带展示）
 */
@Component
public class TechRegimeEvaluator {

    private static final int MIN_BARS = 35;
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal NEAR_LOW_RATIO = new BigDecimal("1.02");
    private static final BigDecimal MA20_DOWN_RATIO = new BigDecimal("0.998");
    private static final BigDecimal RS_BULL = new BigDecimal("3");
    private static final BigDecimal RS_BEAR = new BigDecimal("-3");

    public static final String REGIME_TREND_HOLD = "TREND_HOLD";
    public static final String REGIME_PULLBACK_WATCH = "PULLBACK_WATCH";
    public static final String REGIME_BREAKDOWN_CUT = "BREAKDOWN_CUT";
    public static final String REGIME_REPAIR = "REPAIR";
    public static final String REGIME_NEUTRAL = "NEUTRAL";
    public static final String REGIME_INSUFFICIENT = "INSUFFICIENT";

    public static final String GRADE_STRONG = "STRONG";
    public static final String GRADE_NEUTRAL = "NEUTRAL";
    public static final String GRADE_WEAK = "WEAK";

    public static final String RS_BULLISH = "BULLISH";
    public static final String RS_NEUTRAL = "NEUTRAL";
    public static final String RS_BEARISH = "BEARISH";

    @Resource
    private TechSignalEvaluator techSignalEvaluator;

    /**
     * 评估技术结构状态
     *
     * @param bars 升序日线
     * @param rs20 相对沪深300 的 RS20（百分点，可空）
     * @param rs60 相对沪深300 的 RS60（百分点，可空）
     * @return 评估结果
     */
    public TechRegimeResult evaluate(List<BarDaily> bars, BigDecimal rs20, BigDecimal rs60) {
        List<ObserveTechSignal> radar = techSignalEvaluator.evaluate("BUY", bars);
        int hit = 0;
        for (ObserveTechSignal signal : radar) {
            if (Boolean.TRUE.equals(signal.getHit())) {
                hit++;
            }
        }
        int total = radar.size();
        String rsTone = resolveRsTone(rs20, rs60);

        if (CollUtil.isEmpty(bars) || bars.size() < MIN_BARS) {
            return TechRegimeResult.builder()
                    .regime(REGIME_INSUFFICIENT)
                    .regimeLabel("日线不足")
                    .grade(GRADE_NEUTRAL)
                    .rsTone(rsTone)
                    .summary("日线不足，技术结构暂无法评估")
                    .radarSignals(radar)
                    .hitCount(hit)
                    .total(total)
                    .rs20VsHs300(rs20)
                    .rs60VsHs300(rs60)
                    .build();
        }

        BarSeries series = BarSeries.from(bars);
        int i = series.size() - 1;
        List<BigDecimal> closes = series.getCloses();
        List<BigDecimal> lows = series.getLows();
        BigDecimal close = closes.get(i);
        if (Objects.isNull(close)) {
            return TechRegimeResult.builder()
                    .regime(REGIME_INSUFFICIENT)
                    .regimeLabel("日线不足")
                    .grade(GRADE_NEUTRAL)
                    .rsTone(rsTone)
                    .summary("收盘价缺失，技术结构暂无法评估")
                    .radarSignals(radar)
                    .hitCount(hit)
                    .total(total)
                    .rs20VsHs300(rs20)
                    .rs60VsHs300(rs60)
                    .build();
        }

        BigDecimal ma5 = IndicatorUtils.ma(closes, 5, i);
        BigDecimal ma20 = IndicatorUtils.ma(closes, 20, i);
        BigDecimal ma20Prev5 = i >= 5 ? IndicatorUtils.ma(closes, 20, i - 5) : null;
        BigDecimal low20 = lowest(lows, i, 20);

        List<BigDecimal> difSeries = new ArrayList<>(closes.size());
        for (int k = 0; k < closes.size(); k++) {
            difSeries.add(IndicatorUtils.macdDif(closes, k));
        }
        List<BigDecimal> deaSeries = new ArrayList<>(difSeries.size());
        for (int k = 0; k < difSeries.size(); k++) {
            deaSeries.add(IndicatorUtils.macdDea(difSeries, k));
        }
        BigDecimal dif = difSeries.get(i);
        BigDecimal dea = deaSeries.get(i);
        BigDecimal prevDif = i > 0 ? difSeries.get(i - 1) : null;
        BigDecimal prevDea = i > 0 ? deaSeries.get(i - 1) : null;
        BigDecimal hist = IndicatorUtils.macdHist(dif, dea);
        BigDecimal prevHist = IndicatorUtils.macdHist(prevDif, prevDea);

        boolean aboveMa20 = Objects.nonNull(ma20) && close.compareTo(ma20) >= 0;
        boolean aboveMa5 = Objects.nonNull(ma5) && close.compareTo(ma5) >= 0;
        boolean ma5AboveMa20 = Objects.nonNull(ma5) && Objects.nonNull(ma20) && ma5.compareTo(ma20) >= 0;
        boolean ma20ClearlyDown = Objects.nonNull(ma20) && Objects.nonNull(ma20Prev5)
                && ma20.compareTo(ma20Prev5.multiply(MA20_DOWN_RATIO)) < 0;
        boolean ma20FlatOrDown = Objects.nonNull(ma20) && Objects.nonNull(ma20Prev5)
                && ma20.compareTo(ma20Prev5) <= 0;
        boolean brokenLow = Objects.nonNull(low20) && close.compareTo(low20) <= 0;
        boolean nearLow = Objects.nonNull(low20) && close.compareTo(low20.multiply(NEAR_LOW_RATIO)) <= 0;
        boolean histImproving = Objects.nonNull(hist) && Objects.nonNull(prevHist)
                && hist.compareTo(prevHist) > 0;

        String regime = resolveRegime(aboveMa20, aboveMa5, ma5AboveMa20, ma20ClearlyDown,
                ma20FlatOrDown, brokenLow, nearLow, histImproving);
        String regimeLabel = regimeLabel(regime);
        String grade = resolveGrade(regime, rsTone);
        BigDecimal rsi14 = IndicatorUtils.rsi(closes, 14, i);
        String summary = buildSummary(regimeLabel, rs20, hit, total, rsTone, regime, rsi14);

        return TechRegimeResult.builder()
                .regime(regime)
                .regimeLabel(regimeLabel)
                .grade(grade)
                .rsTone(rsTone)
                .summary(summary)
                .radarSignals(radar)
                .hitCount(hit)
                .total(total)
                .rs20VsHs300(rs20)
                .rs60VsHs300(rs60)
                .build();
    }

    private String resolveRegime(boolean aboveMa20, boolean aboveMa5, boolean ma5AboveMa20,
                                 boolean ma20ClearlyDown, boolean ma20FlatOrDown,
                                 boolean brokenLow, boolean nearLow, boolean histImproving) {
        boolean structureBroken = brokenLow || (!aboveMa20 && ma20FlatOrDown) || (nearLow && !aboveMa20);
        if (structureBroken) {
            if (!brokenLow && aboveMa5 && histImproving) {
                return REGIME_REPAIR;
            }
            return REGIME_BREAKDOWN_CUT;
        }
        // 趋势未破但跌破短均：优先识别为回调（避免 MA5≥MA20 时误判为上升持有）
        if (aboveMa20 && !aboveMa5 && !nearLow) {
            return REGIME_PULLBACK_WATCH;
        }
        if (aboveMa20 && (ma5AboveMa20 || aboveMa5) && !ma20ClearlyDown) {
            return REGIME_TREND_HOLD;
        }
        if (!brokenLow && (aboveMa5 || histImproving) && !aboveMa20) {
            return REGIME_REPAIR;
        }
        return REGIME_NEUTRAL;
    }

    private String resolveRsTone(BigDecimal rs20, BigDecimal rs60) {
        if (Objects.nonNull(rs20) && rs20.compareTo(RS_BULL) >= 0) {
            return RS_BULLISH;
        }
        if (Objects.nonNull(rs20) && rs20.compareTo(RS_BEAR) <= 0
                && (Objects.isNull(rs60) || rs60.compareTo(ZERO) <= 0)) {
            return RS_BEARISH;
        }
        return RS_NEUTRAL;
    }

    private String resolveGrade(String regime, String rsTone) {
        if (REGIME_BREAKDOWN_CUT.equals(regime)) {
            return GRADE_WEAK;
        }
        if (REGIME_TREND_HOLD.equals(regime)) {
            // RS 双弱时不误杀为偏弱，降为中性谨慎
            if (RS_BEARISH.equals(rsTone)) {
                return GRADE_NEUTRAL;
            }
            return GRADE_STRONG;
        }
        return GRADE_NEUTRAL;
    }

    private String buildSummary(String regimeLabel, BigDecimal rs20, int hit, int total,
                                String rsTone, String regime, BigDecimal rsi14) {
        StringBuilder sb = new StringBuilder(regimeLabel);
        if (Objects.nonNull(rs20)) {
            String sign = rs20.signum() > 0 ? "+" : "";
            sb.append(" · RS20 ").append(sign).append(rs20.setScale(1, RoundingMode.HALF_UP).toPlainString());
        }
        if (total > 0) {
            sb.append(" · 雷达 ").append(hit).append('/').append(total);
        }
        if (REGIME_TREND_HOLD.equals(regime) && RS_BEARISH.equals(rsTone)) {
            sb.append(" · 相对大盘偏弱");
        } else if ((REGIME_PULLBACK_WATCH.equals(regime) || REGIME_NEUTRAL.equals(regime))
                && RS_BULLISH.equals(rsTone)) {
            sb.append(" · 相对大盘偏强");
        }
        if (Objects.nonNull(rsi14)) {
            if (rsi14.compareTo(new BigDecimal("72")) >= 0 && REGIME_TREND_HOLD.equals(regime)) {
                sb.append(" · RSI偏热");
            } else if (rsi14.compareTo(new BigDecimal("28")) <= 0
                    && (REGIME_BREAKDOWN_CUT.equals(regime) || REGIME_REPAIR.equals(regime))) {
                sb.append(" · RSI偏冷");
            }
        }
        return sb.toString();
    }

    private String regimeLabel(String regime) {
        return switch (regime) {
            case REGIME_TREND_HOLD -> "上升持有";
            case REGIME_PULLBACK_WATCH -> "回调观察";
            case REGIME_BREAKDOWN_CUT -> "破位减仓";
            case REGIME_REPAIR -> "修复中";
            case REGIME_NEUTRAL -> "中性震荡";
            case REGIME_INSUFFICIENT -> "日线不足";
            default -> "中性震荡";
        };
    }

    private BigDecimal lowest(List<BigDecimal> lows, int end, int period) {
        if (CollUtil.isEmpty(lows) || end < period - 1) {
            return null;
        }
        BigDecimal min = null;
        for (int k = end - period + 1; k <= end; k++) {
            BigDecimal v = lows.get(k);
            if (Objects.isNull(v)) {
                continue;
            }
            if (Objects.isNull(min) || v.compareTo(min) < 0) {
                min = v;
            }
        }
        return min;
    }
}
