package com.awe.apex.quant.screener;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.IntradayAcceptanceMetric;
import com.awe.apex.quant.domain.dto.IntradayPoint;
import com.awe.apex.quant.domain.entity.BarDaily;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * 选股历史与分时指标计算器
 */
@Component
public class ScreenerMetricCalculator {

    /**
     * 统计最近若干根日线中的主板涨停次数。
     *
     * @param bars         升序日线
     * @param lookbackDays 回看交易日数
     * @return 涨停次数
     */
    public int countLimitUps(List<BarDaily> bars, int lookbackDays) {
        if (CollUtil.isEmpty(bars) || lookbackDays <= 0) {
            return 0;
        }
        int start = Math.max(0, bars.size() - lookbackDays);
        int count = 0;
        BigDecimal threshold = new BigDecimal("9.5");
        for (int index = start; index < bars.size(); index++) {
            BigDecimal pctChg = bars.get(index).getPctChg();
            if (Objects.nonNull(pctChg) && pctChg.compareTo(threshold) >= 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * 计算指定窗口涨跌幅。
     *
     * @param bars         升序日线
     * @param lookbackDays 回看交易日数
     * @return 涨跌幅百分比，数据不足时为空
     */
    public BigDecimal calculateReturnPct(List<BarDaily> bars, int lookbackDays) {
        if (Objects.isNull(bars) || lookbackDays <= 0 || bars.size() <= lookbackDays) {
            return null;
        }
        BigDecimal end = bars.get(bars.size() - 1).getClosePrice();
        BigDecimal start = bars.get(bars.size() - 1 - lookbackDays).getClosePrice();
        if (Objects.isNull(end) || Objects.isNull(start) || start.signum() <= 0) {
            return null;
        }
        return end.subtract(start)
                .divide(start, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算连续上涨天数。
     *
     * @param bars 升序日线
     * @return 连续上涨天数
     */
    public int calculateUpDays(List<BarDaily> bars) {
        if (Objects.isNull(bars) || bars.size() < 2) {
            return 0;
        }
        int days = 0;
        for (int index = bars.size() - 1; index >= 1; index--) {
            BigDecimal previous = bars.get(index - 1).getClosePrice();
            BigDecimal current = bars.get(index).getClosePrice();
            if (Objects.isNull(previous) || Objects.isNull(current) || current.compareTo(previous) <= 0) {
                break;
            }
            days++;
        }
        return days;
    }

    /**
     * 计算 ATR 占最新收盘价比例。
     *
     * @param bars   升序日线
     * @param period ATR周期
     * @return 百分比，数据不足时为空
     */
    public BigDecimal calculateAtrPct(List<BarDaily> bars, int period) {
        if (Objects.isNull(bars) || bars.size() < period + 1) {
            return null;
        }
        BigDecimal trueRangeSum = BigDecimal.ZERO;
        int count = 0;
        int start = Math.max(1, bars.size() - period);
        for (int index = start; index < bars.size(); index++) {
            BarDaily current = bars.get(index);
            BigDecimal previousClose = bars.get(index - 1).getClosePrice();
            if (Objects.isNull(current.getHighPrice()) || Objects.isNull(current.getLowPrice())
                    || Objects.isNull(previousClose)) {
                continue;
            }
            BigDecimal highLow = current.getHighPrice().subtract(current.getLowPrice());
            BigDecimal highPrevious = current.getHighPrice().subtract(previousClose).abs();
            BigDecimal lowPrevious = current.getLowPrice().subtract(previousClose).abs();
            trueRangeSum = trueRangeSum.add(highLow.max(highPrevious).max(lowPrevious));
            count++;
        }
        BigDecimal close = bars.get(bars.size() - 1).getClosePrice();
        if (count == 0 || Objects.isNull(close) || close.signum() <= 0) {
            return null;
        }
        return trueRangeSum.divide(BigDecimal.valueOf(count), 6, RoundingMode.HALF_UP)
                .divide(close, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算最新价格在区间最高最低价中的位置。
     *
     * @param bars         升序日线
     * @param lookbackDays 回看交易日数
     * @return 位置百分比，最低为0最高为100
     */
    public BigDecimal calculatePricePositionPct(List<BarDaily> bars, int lookbackDays) {
        if (CollUtil.isEmpty(bars) || lookbackDays <= 0) {
            return null;
        }
        int start = Math.max(0, bars.size() - lookbackDays);
        BigDecimal lowest = null;
        BigDecimal highest = null;
        for (int index = start; index < bars.size(); index++) {
            BarDaily bar = bars.get(index);
            BigDecimal low = Objects.nonNull(bar.getLowPrice()) ? bar.getLowPrice() : bar.getClosePrice();
            BigDecimal high = Objects.nonNull(bar.getHighPrice()) ? bar.getHighPrice() : bar.getClosePrice();
            if (Objects.nonNull(low)) {
                lowest = Objects.isNull(lowest) ? low : lowest.min(low);
            }
            if (Objects.nonNull(high)) {
                highest = Objects.isNull(highest) ? high : highest.max(high);
            }
        }
        BigDecimal close = bars.get(bars.size() - 1).getClosePrice();
        if (Objects.isNull(close) || Objects.isNull(lowest) || Objects.isNull(highest)
                || highest.compareTo(lowest) <= 0) {
            return null;
        }
        return close.subtract(lowest)
                .divide(highest.subtract(lowest), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算指定开始时间后的分时均价承接指标。
     *
     * @param points       分时点
     * @param earliestTime 最早参与时间
     * @return 承接指标
     */
    public IntradayAcceptanceMetric calculateIntradayAcceptance(List<IntradayPoint> points, String earliestTime) {
        int validCount = 0;
        int aboveCount = 0;
        int consecutiveBelow = 0;
        int maxConsecutiveBelow = 0;
        Boolean currentAbove = null;
        String latestTime = null;
        if (Objects.nonNull(points)) {
            for (IntradayPoint point : points) {
                if (Objects.isNull(point) || StringUtils.isBlank(point.getTime())
                        || (StringUtils.isNotBlank(earliestTime) && point.getTime().compareTo(earliestTime) < 0)
                        || Objects.isNull(point.getPrice()) || Objects.isNull(point.getAvgPrice())) {
                    continue;
                }
                boolean above = point.getPrice().compareTo(point.getAvgPrice()) >= 0;
                validCount++;
                if (above) {
                    aboveCount++;
                    consecutiveBelow = 0;
                } else {
                    consecutiveBelow++;
                    maxConsecutiveBelow = Math.max(maxConsecutiveBelow, consecutiveBelow);
                }
                currentAbove = above;
                latestTime = point.getTime();
            }
        }
        BigDecimal ratio = validCount == 0 ? null : BigDecimal.valueOf(aboveCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(validCount), 2, RoundingMode.HALF_UP);
        return IntradayAcceptanceMetric.builder()
                .aboveAvgRatio(ratio)
                .currentAboveAvg(currentAbove)
                .maxConsecutiveBelowMinutes(maxConsecutiveBelow)
                .pointCount(validCount)
                .latestTime(latestTime)
                .build();
    }
}
