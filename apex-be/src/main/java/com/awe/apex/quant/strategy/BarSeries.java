package com.awe.apex.quant.strategy;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.market.MarketCodeUtils;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 日线序列视图
 */
@Getter
public class BarSeries {

    private static final LocalDate CHI_NEXT_REFORM_DATE = LocalDate.of(2020, 8, 24);
    private static final BigDecimal MAIN_BOARD_LIMIT = new BigDecimal("0.10");
    private static final BigDecimal MAIN_BOARD_ST_LIMIT = new BigDecimal("0.05");
    private static final BigDecimal GROWTH_BOARD_LIMIT = new BigDecimal("0.20");
    private static final BigDecimal BEIJING_LIMIT = new BigDecimal("0.30");

    private final List<LocalDate> dates;
    private final List<BigDecimal> opens;
    private final List<BigDecimal> highs;
    private final List<BigDecimal> lows;
    private final List<BigDecimal> closes;
    private final List<BigDecimal> volumes;
    private final List<BigDecimal> priceLimitRates;

    private BarSeries(List<LocalDate> dates, List<BigDecimal> opens, List<BigDecimal> highs,
                      List<BigDecimal> lows, List<BigDecimal> closes, List<BigDecimal> volumes,
                      List<BigDecimal> priceLimitRates) {
        this.dates = dates;
        this.opens = opens;
        this.highs = highs;
        this.lows = lows;
        this.closes = closes;
        this.volumes = volumes;
        this.priceLimitRates = priceLimitRates;
    }

    /**
     * 从日线实体构建（要求已按日期升序）
     *
     * @param bars 日线
     * @return 序列
     */
    public static BarSeries from(List<BarDaily> bars) {
        return from(bars, false);
    }

    /**
     * 从日线实体构建，并携带主板 ST 涨跌停规则
     *
     * @param bars    日线
     * @param stStock 是否主板 ST 股票
     * @return 序列
     */
    public static BarSeries from(List<BarDaily> bars, boolean stStock) {
        List<LocalDate> dates = new ArrayList<>();
        List<BigDecimal> opens = new ArrayList<>();
        List<BigDecimal> highs = new ArrayList<>();
        List<BigDecimal> lows = new ArrayList<>();
        List<BigDecimal> closes = new ArrayList<>();
        List<BigDecimal> volumes = new ArrayList<>();
        List<BigDecimal> priceLimitRates = new ArrayList<>();
        for (BarDaily bar : bars) {
            dates.add(bar.getTradeDate());
            opens.add(bar.getOpenPrice());
            highs.add(bar.getHighPrice());
            lows.add(bar.getLowPrice());
            closes.add(bar.getClosePrice());
            volumes.add(bar.getVolume());
            priceLimitRates.add(resolvePriceLimitRate(bar.getCode(), bar.getTradeDate(), stStock));
        }
        return new BarSeries(
                Collections.unmodifiableList(dates),
                Collections.unmodifiableList(opens),
                Collections.unmodifiableList(highs),
                Collections.unmodifiableList(lows),
                Collections.unmodifiableList(closes),
                Collections.unmodifiableList(volumes),
                Collections.unmodifiableList(priceLimitRates));
    }

    /**
     * 构建仅包含指定位置之前数据的只读语义快照
     *
     * @param endExclusive 结束位置，不包含
     * @return 历史序列快照
     */
    public BarSeries prefix(int endExclusive) {
        if (endExclusive < 0 || endExclusive > size()) {
            throw new BusinessException("历史序列结束位置超出范围");
        }
        return new BarSeries(
                dates.subList(0, endExclusive),
                opens.subList(0, endExclusive),
                highs.subList(0, endExclusive),
                lows.subList(0, endExclusive),
                closes.subList(0, endExclusive),
                volumes.subList(0, endExclusive),
                priceLimitRates.subList(0, endExclusive));
    }

    /**
     * 长度
     *
     * @return size
     */
    public int size() {
        return closes.size();
    }

    private static BigDecimal resolvePriceLimitRate(String code, LocalDate tradeDate, boolean stStock) {
        String normalizedCode = MarketCodeUtils.normalizeCode(code);
        if (MarketCodeUtils.isBj(normalizedCode)) {
            return BEIJING_LIMIT;
        }
        if (Objects.nonNull(normalizedCode)
                && (normalizedCode.startsWith("688") || normalizedCode.startsWith("689"))) {
            return GROWTH_BOARD_LIMIT;
        }
        if (Objects.nonNull(normalizedCode)
                && (normalizedCode.startsWith("300") || normalizedCode.startsWith("301"))
                && (Objects.isNull(tradeDate) || !tradeDate.isBefore(CHI_NEXT_REFORM_DATE))) {
            return GROWTH_BOARD_LIMIT;
        }
        return stStock ? MAIN_BOARD_ST_LIMIT : MAIN_BOARD_LIMIT;
    }
}
