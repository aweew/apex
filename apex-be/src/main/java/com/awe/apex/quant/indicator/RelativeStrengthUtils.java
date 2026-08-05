package com.awe.apex.quant.indicator;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.quant.domain.entity.BarDaily;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * 相对强弱（个股收益 − 基准收益）
 */
public final class RelativeStrengthUtils {

    private RelativeStrengthUtils() {
    }

    /**
     * 相对强弱百分点：个股区间收益 − 基准区间收益，再 ×100
     *
     * @param stock    升序个股日线
     * @param bench    升序基准日线
     * @param lookback 回溯交易日数
     * @return 百分点；数据不足返回 null
     */
    public static BigDecimal relativeStrengthPct(List<BarDaily> stock, List<BarDaily> bench, int lookback) {
        BigDecimal stockRet = periodReturn(stock, lookback);
        BigDecimal benchRet = periodReturn(bench, lookback);
        if (Objects.isNull(stockRet) || Objects.isNull(benchRet)) {
            return null;
        }
        return stockRet.subtract(benchRet).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 区间简单收益率
     *
     * @param bars     升序日线
     * @param lookback 回溯交易日数
     * @return 收益率；数据不足返回 null
     */
    public static BigDecimal periodReturn(List<BarDaily> bars, int lookback) {
        if (CollUtil.isEmpty(bars) || bars.size() <= lookback || lookback <= 0) {
            return null;
        }
        BigDecimal end = bars.get(bars.size() - 1).getClosePrice();
        BigDecimal start = bars.get(bars.size() - 1 - lookback).getClosePrice();
        if (Objects.isNull(end) || Objects.isNull(start) || start.signum() <= 0) {
            return null;
        }
        return end.subtract(start).divide(start, 6, RoundingMode.HALF_UP);
    }
}
