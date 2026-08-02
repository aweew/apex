package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.ScreenerReq;
import com.awe.apex.quant.domain.dto.WatchlistResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.service.IScreenerService;
import com.awe.apex.quant.service.IWatchlistService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    private static final String MARKET_SCOPE = "__MARKET__";

    @Resource
    private IWatchlistService watchlistService;

    @Resource
    private BarDailyMapper barDailyMapper;

    @Resource
    private StockBasicMapper stockBasicMapper;

    /**
     * 运行选股
     *
     * @param req 条件
     * @return 结果
     */
    @Override
    public List<WatchlistResp> run(ScreenerReq req) {
        ScreenerReq safe = Objects.nonNull(req) ? req : new ScreenerReq();
        boolean marketScope = isMarketScope(safe.getGroupName());
        List<WatchlistResp> list = marketScope
                ? listMarketUniverse(safe)
                : watchlistService.listWatchlist(normalizeGroupName(safe.getGroupName()));
        boolean excludeSt = !Boolean.FALSE.equals(safe.getExcludeSt());
        boolean excludeLimitUp = Boolean.TRUE.equals(safe.getExcludeLimitUp());
        boolean excludeLimitDown = Boolean.TRUE.equals(safe.getExcludeLimitDown());
        int minBars = Objects.nonNull(safe.getMinBars()) ? safe.getMinBars() : 0;
        int limit = Objects.nonNull(safe.getLimit()) ? Math.max(1, Math.min(safe.getLimit(), 200)) : 50;
        BigDecimal limitUp = new BigDecimal("9.5");
        BigDecimal limitDown = new BigDecimal("-9.5");
        boolean needBarMetrics = Objects.nonNull(safe.getMinVolumeRatio())
                || Objects.nonNull(safe.getMinUpDays())
                || Objects.nonNull(safe.getMaxAtrPct())
                || Objects.nonNull(safe.getMinAtrPct());

        // 全市场候选已在 SQL 侧做了基础过滤，这里再统一过一遍（含自选）
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
            if (minBars > 0 && Objects.nonNull(row.getBarCount()) && row.getBarCount() < minBars) {
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

    /**
     * 空 / 全部市场 / __MARKET__ 视为全市场；其余走自选分组
     */
    private boolean isMarketScope(String groupName) {
        if (StringUtils.isBlank(groupName)) {
            return true;
        }
        String text = groupName.trim();
        return MARKET_SCOPE.equalsIgnoreCase(text)
                || "全部市场".equals(text)
                || "全市场".equals(text)
                || "ALL".equalsIgnoreCase(text);
    }

    private String normalizeGroupName(String groupName) {
        if (StringUtils.isBlank(groupName)) {
            return null;
        }
        return groupName.trim();
    }

    /**
     * 全市场候选：先在 stock_basic 上按估值/涨跌/市值预筛，再补 K 线根数
     */
    private List<WatchlistResp> listMarketUniverse(ScreenerReq safe) {
        boolean excludeSt = !Boolean.FALSE.equals(safe.getExcludeSt());
        boolean excludeLimitUp = Boolean.TRUE.equals(safe.getExcludeLimitUp());
        boolean excludeLimitDown = Boolean.TRUE.equals(safe.getExcludeLimitDown());
        int limit = Objects.nonNull(safe.getLimit()) ? Math.max(1, Math.min(safe.getLimit(), 200)) : 50;
        int candidateLimit = Math.min(Math.max(limit * 10, 300), 1000);
        BigDecimal limitUp = new BigDecimal("9.5");
        BigDecimal limitDown = new BigDecimal("-9.5");

        LambdaQueryWrapper<StockBasic> qw = Wrappers.<StockBasic>lambdaQuery();
        if (excludeSt) {
            qw.and(w -> w.isNull(StockBasic::getStFlag).or().eq(StockBasic::getStFlag, 0));
            qw.notLike(StockBasic::getName, "ST");
        }
        if (Objects.nonNull(safe.getPeMin())) {
            qw.ge(StockBasic::getPeTtm, safe.getPeMin());
        }
        if (Objects.nonNull(safe.getPeMax())) {
            qw.gt(StockBasic::getPeTtm, 0).le(StockBasic::getPeTtm, safe.getPeMax());
        }
        if (Objects.nonNull(safe.getPbMin())) {
            qw.ge(StockBasic::getPb, safe.getPbMin());
        }
        if (Objects.nonNull(safe.getPbMax())) {
            qw.gt(StockBasic::getPb, 0).le(StockBasic::getPb, safe.getPbMax());
        }
        if (StringUtils.isNotBlank(safe.getIndustry())) {
            qw.like(StockBasic::getIndustry, safe.getIndustry().trim());
        }
        if (Objects.nonNull(safe.getPctChgMin())) {
            qw.ge(StockBasic::getPctChg, safe.getPctChgMin());
        }
        if (Objects.nonNull(safe.getPctChgMax())) {
            qw.le(StockBasic::getPctChg, safe.getPctChgMax());
        }
        if (excludeLimitUp) {
            qw.and(w -> w.isNull(StockBasic::getPctChg).or().lt(StockBasic::getPctChg, limitUp));
        }
        if (excludeLimitDown) {
            qw.and(w -> w.isNull(StockBasic::getPctChg).or().gt(StockBasic::getPctChg, limitDown));
        }
        if (Objects.nonNull(safe.getMinCircMv())) {
            qw.ge(StockBasic::getCircMv, safe.getMinCircMv());
        }
        if (Objects.nonNull(safe.getMaxCircMv())) {
            qw.le(StockBasic::getCircMv, safe.getMaxCircMv());
        }
        qw.orderByDesc(StockBasic::getPctChg).last("LIMIT " + candidateLimit);

        List<StockBasic> basics = stockBasicMapper.selectList(qw);
        if (CollUtil.isEmpty(basics)) {
            return new ArrayList<>();
        }

        List<String> codes = new ArrayList<>();
        for (StockBasic basic : basics) {
            if (StringUtils.isNotBlank(basic.getCode())) {
                codes.add(basic.getCode());
            }
        }
        Map<String, Integer> barCountMap = loadBarCounts(codes);
        List<WatchlistResp> rows = new ArrayList<>();
        for (StockBasic basic : basics) {
            rows.add(WatchlistResp.builder()
                    .code(basic.getCode())
                    .name(basic.getName())
                    .market(basic.getMarket())
                    .groupName("全部市场")
                    .source(basic.getSource())
                    .latestPrice(basic.getLatestPrice())
                    .pctChg(basic.getPctChg())
                    .peTtm(basic.getPeTtm())
                    .pb(basic.getPb())
                    .industry(basic.getIndustry())
                    .totalMv(basic.getTotalMv())
                    .circMv(basic.getCircMv())
                    .barCount(barCountMap.getOrDefault(basic.getCode(), 0))
                    .build());
        }
        return rows;
    }

    private Map<String, Integer> loadBarCounts(List<String> codes) {
        Map<String, Integer> map = new HashMap<>();
        if (CollUtil.isEmpty(codes)) {
            return map;
        }
        // 分批，避免 IN 过长
        int batchSize = 400;
        for (int i = 0; i < codes.size(); i += batchSize) {
            List<String> batch = codes.subList(i, Math.min(i + batchSize, codes.size()));
            List<Map<String, Object>> stats = barDailyMapper.selectMaps(Wrappers.<BarDaily>query()
                    .select("code", "COUNT(1) AS cnt")
                    .in("code", batch)
                    .groupBy("code"));
            for (Map<String, Object> row : stats) {
                Object codeObj = row.get("code");
                Object cnt = row.get("cnt");
                if (Objects.isNull(codeObj) || Objects.isNull(cnt)) {
                    continue;
                }
                map.put(String.valueOf(codeObj), Integer.parseInt(String.valueOf(cnt)));
            }
        }
        return map;
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
        // 控制全市场候选量时的拉取上限
        int hardCap = Math.min(codes.size() * per, 20000);
        List<BarDaily> all = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .in(BarDaily::getCode, codes)
                .orderByDesc(BarDaily::getTradeDate)
                .last("LIMIT " + hardCap));
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
        return atr.divide(close, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
