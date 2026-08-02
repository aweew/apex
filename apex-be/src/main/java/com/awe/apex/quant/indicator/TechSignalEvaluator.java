package com.awe.apex.quant.indicator;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.quant.domain.dto.ObserveTechSignal;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.strategy.BarSeries;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 个股技术雷达（与观察池口径一致，可供综合分析复用）
 */
@Component
public class TechSignalEvaluator {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal VOL_SURGE = new BigDecimal("1.5");

    /**
     * 评估技术信号
     *
     * @param side BUY/SELL
     * @param bars 升序日线
     * @return 信号列表
     */
    public List<ObserveTechSignal> evaluate(String side, List<BarDaily> bars) {
        List<ObserveTechSignal> list = new ArrayList<>();
        if (CollUtil.isEmpty(bars) || bars.size() < 35) {
            return list;
        }
        BarSeries series = BarSeries.from(bars);
        int i = series.size() - 1;
        List<BigDecimal> closes = series.getCloses();
        List<BigDecimal> highs = series.getHighs();
        List<BigDecimal> lows = series.getLows();
        List<BigDecimal> volumes = series.getVolumes();
        BigDecimal close = closes.get(i);
        if (Objects.isNull(close)) {
            return list;
        }
        boolean sell = "SELL".equalsIgnoreCase(side);

        BigDecimal ma5 = IndicatorUtils.ma(closes, 5, i);
        BigDecimal ma10 = IndicatorUtils.ma(closes, 10, i);
        BigDecimal ma20 = IndicatorUtils.ma(closes, 20, i);
        BigDecimal volMa5 = IndicatorUtils.ma(volumes, 5, i);
        BigDecimal rsi = IndicatorUtils.rsi(closes, 14, i);
        BigDecimal high20 = IndicatorUtils.highest(highs, 20, i);
        BigDecimal low20 = null;
        if (i >= 19) {
            low20 = lows.get(i - 19);
            for (int k = i - 18; k <= i; k++) {
                BigDecimal v = lows.get(k);
                if (Objects.nonNull(v) && (Objects.isNull(low20) || v.compareTo(low20) < 0)) {
                    low20 = v;
                }
            }
        }

        List<BigDecimal> difSeries = new ArrayList<>(closes.size());
        for (int k = 0; k < closes.size(); k++) {
            difSeries.add(IndicatorUtils.macdDif(closes, k));
        }
        List<BigDecimal> deaSeries = buildDeaSeries(difSeries);
        BigDecimal dif = difSeries.get(i);
        BigDecimal dea = deaSeries.get(i);
        BigDecimal prevDif = i > 0 ? difSeries.get(i - 1) : null;
        BigDecimal prevDea = i > 0 ? deaSeries.get(i - 1) : null;
        BigDecimal hist = IndicatorUtils.macdHist(dif, dea);
        BigDecimal prevHist = IndicatorUtils.macdHist(prevDif, prevDea);
        boolean macdGold = IndicatorUtils.crossedUp(prevDif, prevDea, dif, dea);
        boolean macdDead = IndicatorUtils.crossedDown(prevDif, prevDea, dif, dea);
        boolean macdZone = Objects.nonNull(dif) && Objects.nonNull(dea) && dif.compareTo(dea) > 0;

        boolean aboveMa5 = Objects.nonNull(ma5) && close.compareTo(ma5) >= 0;
        list.add(signal("MA5", sell ? "跌破5日" : "站稳5日",
                sell ? !aboveMa5 : aboveMa5,
                Objects.nonNull(ma5) ? ("MA5 " + ma5.stripTrailingZeros().toPlainString()) : null));

        boolean aboveMa20 = Objects.nonNull(ma20) && close.compareTo(ma20) >= 0;
        list.add(signal("MA20", sell ? "跌破20日" : "站稳20日",
                sell ? !aboveMa20 : aboveMa20,
                Objects.nonNull(ma20) ? ("MA20 " + ma20.stripTrailingZeros().toPlainString()) : null));

        boolean maBull = Objects.nonNull(ma5) && Objects.nonNull(ma10) && Objects.nonNull(ma20)
                && ma5.compareTo(ma10) > 0 && ma10.compareTo(ma20) > 0;
        boolean maBear = Objects.nonNull(ma5) && Objects.nonNull(ma10) && Objects.nonNull(ma20)
                && ma5.compareTo(ma10) < 0 && ma10.compareTo(ma20) < 0;
        list.add(signal("MA_ALIGN", sell ? "空头排列" : "多头排列",
                sell ? maBear : maBull, null));

        boolean macdHit = sell ? (macdDead || !macdZone) : (macdGold || macdZone);
        if (Objects.isNull(dif) || Objects.isNull(dea)) {
            macdHit = false;
        }
        String macdDetail = macdGold ? "今日金叉" : macdDead ? "今日死叉"
                : macdZone ? "DIF>DEA" : "DIF≤DEA";
        list.add(signal("MACD", sell ? "MACD死叉/空" : "MACD金叉/多", macdHit, macdDetail));

        boolean histUp = Objects.nonNull(hist) && Objects.nonNull(prevHist)
                && hist.compareTo(ZERO) > 0 && hist.compareTo(prevHist) > 0;
        boolean histDown = Objects.nonNull(hist) && Objects.nonNull(prevHist)
                && hist.compareTo(ZERO) < 0 && hist.compareTo(prevHist) < 0;
        list.add(signal("MACD_HIST", sell ? "绿柱扩大" : "红柱放大",
                sell ? histDown : histUp,
                Objects.nonNull(hist) ? hist.stripTrailingZeros().toPlainString() : null));

        BigDecimal vol = volumes.get(i);
        boolean volSurge = Objects.nonNull(vol) && Objects.nonNull(volMa5) && volMa5.signum() > 0
                && vol.compareTo(volMa5.multiply(VOL_SURGE)) >= 0;
        String volDetail = null;
        if (Objects.nonNull(vol) && Objects.nonNull(volMa5) && volMa5.signum() > 0) {
            volDetail = "量比 "
                    + vol.divide(volMa5, 2, RoundingMode.HALF_UP).toPlainString();
        }
        list.add(signal("VOL", "放量确认", volSurge, volDetail));

        boolean rsiBuyOk = Objects.nonNull(rsi) && rsi.compareTo(new BigDecimal("40")) >= 0
                && rsi.compareTo(new BigDecimal("72")) <= 0;
        boolean rsiSellOk = Objects.nonNull(rsi) && rsi.compareTo(new BigDecimal("55")) <= 0;
        list.add(signal("RSI", sell ? "RSI转弱" : "RSI健康",
                sell ? rsiSellOk : rsiBuyOk,
                Objects.nonNull(rsi) ? ("RSI " + rsi.setScale(1, RoundingMode.HALF_UP)) : null));

        boolean breakHigh = Objects.nonNull(high20) && close.compareTo(high20) >= 0;
        boolean breakLow = Objects.nonNull(low20) && close.compareTo(low20) <= 0;
        list.add(signal("BREAK", sell ? "破20日低" : "近20日高",
                sell ? breakLow : breakHigh, null));

        return list;
    }

    /**
     * 计算 ATR14（价格单位）
     *
     * @param bars 升序日线
     * @return ATR
     */
    public BigDecimal calcAtr14(List<BarDaily> bars) {
        if (CollUtil.isEmpty(bars) || bars.size() < 15) {
            return null;
        }
        int end = bars.size() - 1;
        int start = end - 13;
        BigDecimal sum = ZERO;
        int n = 0;
        for (int i = start; i <= end; i++) {
            if (i <= 0) {
                continue;
            }
            BarDaily cur = bars.get(i);
            BarDaily prev = bars.get(i - 1);
            if (Objects.isNull(cur.getHighPrice()) || Objects.isNull(cur.getLowPrice())
                    || Objects.isNull(prev.getClosePrice())) {
                continue;
            }
            BigDecimal tr1 = cur.getHighPrice().subtract(cur.getLowPrice());
            BigDecimal tr2 = cur.getHighPrice().subtract(prev.getClosePrice()).abs();
            BigDecimal tr3 = cur.getLowPrice().subtract(prev.getClosePrice()).abs();
            sum = sum.add(tr1.max(tr2).max(tr3));
            n++;
        }
        if (n == 0) {
            return null;
        }
        return sum.divide(BigDecimal.valueOf(n), 4, RoundingMode.HALF_UP);
    }

    private ObserveTechSignal signal(String key, String label, boolean hit, String detail) {
        return ObserveTechSignal.builder()
                .key(key)
                .label(label)
                .hit(hit)
                .detail(detail)
                .build();
    }

    private List<BigDecimal> buildDeaSeries(List<BigDecimal> difSeries) {
        List<BigDecimal> deaSeries = new ArrayList<>(difSeries.size());
        for (int i = 0; i < difSeries.size(); i++) {
            deaSeries.add(IndicatorUtils.macdDea(difSeries, i));
        }
        return deaSeries;
    }
}
