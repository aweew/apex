package com.awe.apex.quant.service.impl;

import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.ScreenerReq;
import com.awe.apex.quant.domain.dto.WatchlistResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.service.IScreenerService;
import com.awe.apex.quant.service.IWatchlistService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 条件选股实现
 */
@Service
public class ScreenerServiceImpl implements IScreenerService {

    @Resource
    private IWatchlistService watchlistService;

    @Resource
    private BarDailyMapper barDailyMapper;

    /**
     * 运行选股
     *
     * @param req 条件
     * @return 结果
     */
    @Override
    public List<WatchlistResp> run(ScreenerReq req) {
        ScreenerReq safe = Objects.nonNull(req) ? req : new ScreenerReq();
        List<WatchlistResp> list = watchlistService.listWatchlist(safe.getGroupName());
        boolean excludeSt = !Boolean.FALSE.equals(safe.getExcludeSt());
        boolean excludeLimitUp = Boolean.TRUE.equals(safe.getExcludeLimitUp());
        boolean excludeLimitDown = Boolean.TRUE.equals(safe.getExcludeLimitDown());
        int minBars = Objects.nonNull(safe.getMinBars()) ? safe.getMinBars() : 60;
        int limit = Objects.nonNull(safe.getLimit()) ? Math.max(1, Math.min(safe.getLimit(), 200)) : 50;
        BigDecimal limitUp = new BigDecimal("9.5");
        BigDecimal limitDown = new BigDecimal("-9.5");
        boolean needBarMetrics = Objects.nonNull(safe.getMinVolumeRatio())
                || Objects.nonNull(safe.getMinUpDays())
                || Objects.nonNull(safe.getMaxAtrPct())
                || Objects.nonNull(safe.getMinAtrPct());

        List<WatchlistResp> filtered = new ArrayList<>();
        for (WatchlistResp row : list) {
            String name = row.getName();
            if (excludeSt && StringUtils.isNotBlank(name) && name.toUpperCase().contains("ST")) {
                continue;
            }
            if (excludeLimitUp && Objects.nonNull(row.getPctChg()) && row.getPctChg().compareTo(limitUp) >= 0) {
                continue;
            }
            if (excludeLimitDown && Objects.nonNull(row.getPctChg()) && row.getPctChg().compareTo(limitDown) <= 0) {
                continue;
            }
            if (Objects.nonNull(row.getBarCount()) && row.getBarCount() < minBars) {
                continue;
            }
            if (Objects.nonNull(safe.getPeMin()) && (Objects.isNull(row.getPeTtm())
                    || row.getPeTtm().compareTo(safe.getPeMin()) < 0)) {
                continue;
            }
            if (Objects.nonNull(safe.getPeMax()) && (Objects.isNull(row.getPeTtm())
                    || row.getPeTtm().signum() <= 0
                    || row.getPeTtm().compareTo(safe.getPeMax()) > 0)) {
                continue;
            }
            if (Objects.nonNull(safe.getPbMin()) && (Objects.isNull(row.getPb())
                    || row.getPb().compareTo(safe.getPbMin()) < 0)) {
                continue;
            }
            if (Objects.nonNull(safe.getPbMax()) && (Objects.isNull(row.getPb())
                    || row.getPb().signum() <= 0
                    || row.getPb().compareTo(safe.getPbMax()) > 0)) {
                continue;
            }
            if (StringUtils.isNotBlank(safe.getIndustry())
                    && (StringUtils.isBlank(row.getIndustry()) || !row.getIndustry().contains(safe.getIndustry()))) {
                continue;
            }
            if (Objects.nonNull(safe.getPctChgMin())
                    && (Objects.isNull(row.getPctChg()) || row.getPctChg().compareTo(safe.getPctChgMin()) < 0)) {
                continue;
            }
            if (Objects.nonNull(safe.getPctChgMax())
                    && (Objects.isNull(row.getPctChg()) || row.getPctChg().compareTo(safe.getPctChgMax()) > 0)) {
                continue;
            }
            if (Objects.nonNull(safe.getPctChg20Min())
                    && (Objects.isNull(row.getPctChg20()) || row.getPctChg20().compareTo(safe.getPctChg20Min()) < 0)) {
                continue;
            }
            if (Objects.nonNull(safe.getPctChg20Max())
                    && (Objects.isNull(row.getPctChg20()) || row.getPctChg20().compareTo(safe.getPctChg20Max()) > 0)) {
                continue;
            }
            if (Objects.nonNull(safe.getMinCircMv())
                    && (Objects.isNull(row.getCircMv()) || row.getCircMv().compareTo(safe.getMinCircMv()) < 0)) {
                continue;
            }
            if (Objects.nonNull(safe.getMaxCircMv())
                    && (Objects.isNull(row.getCircMv()) || row.getCircMv().compareTo(safe.getMaxCircMv()) > 0)) {
                continue;
            }
            if (Objects.nonNull(safe.getRs20Min())
                    && (Objects.isNull(row.getRs20VsHs300()) || row.getRs20VsHs300().compareTo(safe.getRs20Min()) < 0)) {
                continue;
            }
            filtered.add(row);
        }

        if (needBarMetrics && !filtered.isEmpty()) {
            Map<String, List<BarDaily>> barsByCode = loadRecentBars(filtered, 40);
            List<WatchlistResp> withBars = new ArrayList<>();
            for (WatchlistResp row : filtered) {
                List<BarDaily> bars = barsByCode.getOrDefault(row.getCode(), List.of());
                BigDecimal volRatio = calcVolumeRatio(bars, 20);
                int upDays = calcUpDays(bars);
                BigDecimal atrPct = calcAtrPct(bars, 14);
                row.setVolumeRatio(volRatio);
                row.setUpDays(upDays);
                row.setAtrPct(atrPct);
                if (Objects.nonNull(safe.getMinVolumeRatio())
                        && (Objects.isNull(volRatio) || volRatio.compareTo(safe.getMinVolumeRatio()) < 0)) {
                    continue;
                }
                if (Objects.nonNull(safe.getMinUpDays()) && upDays < safe.getMinUpDays()) {
                    continue;
                }
                if (Objects.nonNull(safe.getMaxAtrPct())
                        && (Objects.isNull(atrPct) || atrPct.compareTo(safe.getMaxAtrPct()) > 0)) {
                    continue;
                }
                if (Objects.nonNull(safe.getMinAtrPct())
                        && (Objects.isNull(atrPct) || atrPct.compareTo(safe.getMinAtrPct()) < 0)) {
                    continue;
                }
                withBars.add(row);
            }
            filtered = withBars;
        }

        filtered.sort(Comparator
                .comparing((WatchlistResp r) -> Objects.nonNull(r.getPctChg()) ? r.getPctChg() : new BigDecimal("-999"),
                        Comparator.reverseOrder())
                .thenComparing(r -> Objects.nonNull(r.getPeTtm()) ? r.getPeTtm() : new BigDecimal("9999")));
        if (filtered.size() > limit) {
            return filtered.subList(0, limit);
        }
        return filtered;
    }

    private Map<String, List<BarDaily>> loadRecentBars(List<WatchlistResp> rows, int limitPerCode) {
        Map<String, List<BarDaily>> map = new HashMap<>();
        List<String> codes = new ArrayList<>();
        for (WatchlistResp row : rows) {
            if (StringUtils.isNotBlank(row.getCode())) {
                codes.add(row.getCode());
            }
        }
        if (codes.isEmpty()) {
            return map;
        }
        int per = Math.max(5, Math.min(limitPerCode, 120));
        // 批量拉取后按 code 截取最近 N 根（倒序扫描首见）
        List<BarDaily> all = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .in(BarDaily::getCode, codes)
                .orderByDesc(BarDaily::getTradeDate)
                .last("LIMIT " + Math.min(codes.size() * per, 12000)));
        Map<String, List<BarDaily>> descBuckets = new HashMap<>();
        for (BarDaily bar : all) {
            List<BarDaily> bucket = descBuckets.computeIfAbsent(bar.getCode(), k -> new ArrayList<>());
            if (bucket.size() < per) {
                bucket.add(bar);
            }
        }
        for (Map.Entry<String, List<BarDaily>> entry : descBuckets.entrySet()) {
            List<BarDaily> asc = new ArrayList<>(entry.getValue());
            asc.sort(Comparator.comparing(BarDaily::getTradeDate));
            map.put(entry.getKey(), asc);
        }
        return map;
    }

    private BigDecimal calcVolumeRatio(List<BarDaily> bars, int lookback) {
        if (bars == null || bars.size() <= lookback) {
            return null;
        }
        BigDecimal lastVol = bars.get(bars.size() - 1).getVolume();
        if (Objects.isNull(lastVol) || lastVol.signum() <= 0) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        int n = 0;
        for (int i = bars.size() - 1 - lookback; i < bars.size() - 1; i++) {
            BigDecimal v = bars.get(i).getVolume();
            if (Objects.nonNull(v) && v.signum() > 0) {
                sum = sum.add(v);
                n++;
            }
        }
        if (n == 0) {
            return null;
        }
        BigDecimal avg = sum.divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP);
        if (avg.signum() <= 0) {
            return null;
        }
        return lastVol.divide(avg, 2, RoundingMode.HALF_UP);
    }

    private int calcUpDays(List<BarDaily> bars) {
        if (bars == null || bars.size() < 2) {
            return 0;
        }
        int days = 0;
        for (int i = bars.size() - 1; i >= 1; i--) {
            BigDecimal c0 = bars.get(i - 1).getClosePrice();
            BigDecimal c1 = bars.get(i).getClosePrice();
            if (Objects.isNull(c0) || Objects.isNull(c1) || c1.compareTo(c0) <= 0) {
                break;
            }
            days++;
        }
        return days;
    }

    private BigDecimal calcAtrPct(List<BarDaily> ascBars, int period) {
        if (ascBars == null || ascBars.size() < period + 1) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        int n = 0;
        int start = Math.max(1, ascBars.size() - period);
        for (int i = start; i < ascBars.size(); i++) {
            BarDaily cur = ascBars.get(i);
            BarDaily prev = ascBars.get(i - 1);
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
        BigDecimal atr = sum.divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP);
        BigDecimal close = ascBars.get(ascBars.size() - 1).getClosePrice();
        if (Objects.isNull(close) || close.signum() <= 0) {
            return null;
        }
        return atr.divide(close, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }
}
