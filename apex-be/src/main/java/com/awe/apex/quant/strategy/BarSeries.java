package com.awe.apex.quant.strategy;

import com.awe.apex.quant.domain.entity.BarDaily;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 日线序列视图
 */
@Getter
public class BarSeries {

    private final List<LocalDate> dates = new ArrayList<>();
    private final List<BigDecimal> opens = new ArrayList<>();
    private final List<BigDecimal> highs = new ArrayList<>();
    private final List<BigDecimal> lows = new ArrayList<>();
    private final List<BigDecimal> closes = new ArrayList<>();
    private final List<BigDecimal> volumes = new ArrayList<>();

    /**
     * 从日线实体构建（要求已按日期升序）
     *
     * @param bars 日线
     * @return 序列
     */
    public static BarSeries from(List<BarDaily> bars) {
        BarSeries series = new BarSeries();
        for (BarDaily bar : bars) {
            series.dates.add(bar.getTradeDate());
            series.opens.add(bar.getOpenPrice());
            series.highs.add(bar.getHighPrice());
            series.lows.add(bar.getLowPrice());
            series.closes.add(bar.getClosePrice());
            series.volumes.add(bar.getVolume());
        }
        return series;
    }

    /**
     * 长度
     *
     * @return size
     */
    public int size() {
        return closes.size();
    }
}
