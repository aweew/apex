package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.BarSyncReq;
import com.awe.apex.quant.domain.dto.StockDetailResp;
import com.awe.apex.quant.domain.dto.StockSearchItem;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.Watchlist;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.WatchlistMapper;
import com.awe.apex.quant.market.MarketCodeUtils;
import com.awe.apex.quant.market.StockQuoteClient;
import com.awe.apex.quant.service.IBarDailyService;
import com.awe.apex.quant.service.IStockService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 股票基本信息服务实现
 */
@Service
public class StockServiceImpl implements IStockService {

    @Resource
    private StockQuoteClient stockQuoteClient;

    @Resource
    private StockBasicMapper stockBasicMapper;

    @Resource
    private BarDailyMapper barDailyMapper;

    @Resource
    private IBarDailyService barDailyService;

    @Resource
    private WatchlistMapper watchlistMapper;

    /**
     * 同步并落库基本信息
     *
     * @param code 证券代码
     * @return 基本信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockBasic syncBasic(String code) {
        String pure = MarketCodeUtils.normalizeCode(code);
        if (StringUtils.isBlank(pure)) {
            throw new BusinessException("代码无效");
        }
        StockBasic fetched = stockQuoteClient.fetchBasic(pure);
        StockBasic existing = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                .eq(StockBasic::getCode, pure)
                .last("limit 1"));
        LocalDateTime now = LocalDateTime.now();
        if (Objects.isNull(existing)) {
            fetched.setCreateTime(now);
            fetched.setUpdateTime(now);
            stockBasicMapper.insert(fetched);
            return fetched;
        }
        existing.setName(fetched.getName());
        existing.setMarket(fetched.getMarket());
        existing.setStFlag(fetched.getStFlag());
        existing.setPeTtm(fetched.getPeTtm());
        existing.setPb(fetched.getPb());
        existing.setTotalMv(fetched.getTotalMv());
        existing.setCircMv(fetched.getCircMv());
        existing.setIndustry(fetched.getIndustry());
        existing.setLatestPrice(fetched.getLatestPrice());
        existing.setPctChg(fetched.getPctChg());
        existing.setSource(fetched.getSource());
        existing.setQuoteTime(fetched.getQuoteTime());
        if (Objects.nonNull(fetched.getListDate())) {
            existing.setListDate(fetched.getListDate());
        }
        existing.setUpdateTime(now);
        stockBasicMapper.updateById(existing);
        return existing;
    }

    /**
     * 查询详情
     *
     * @param code     证券代码
     * @param barLimit K 线条数
     * @param refresh  是否强制刷新基本信息
     * @return 详情
     */
    @Override
    public StockDetailResp detail(String code, Integer barLimit, Boolean refresh) {
        String pure = MarketCodeUtils.normalizeCode(code);
        int limit = Objects.isNull(barLimit) ? 120 : Math.max(30, Math.min(barLimit, 500));

        StockBasic basic = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                .eq(StockBasic::getCode, pure)
                .last("limit 1"));
        boolean needRefresh = Boolean.TRUE.equals(refresh)
                || Objects.isNull(basic)
                || Objects.isNull(basic.getQuoteTime())
                || basic.getQuoteTime().isBefore(LocalDateTime.now().minusMinutes(30));
        if (needRefresh) {
            basic = syncBasic(pure);
        }

        List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, pure)
                .orderByDesc(BarDaily::getTradeDate)
                .last("limit " + limit));
        if (bars.size() < 30) {
            BarSyncReq syncReq = new BarSyncReq();
            syncReq.setCodes(Collections.singletonList(pure));
            syncReq.setBeginDate(LocalDate.now().minusYears(2).toString());
            syncReq.setEndDate(LocalDate.now().toString());
            barDailyService.syncBars(syncReq);
            bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                    .eq(BarDaily::getCode, pure)
                    .orderByDesc(BarDaily::getTradeDate)
                    .last("limit " + limit));
        }
        bars.sort((a, b) -> a.getTradeDate().compareTo(b.getTradeDate()));

        BigDecimal rs20 = null;
        BigDecimal rs60 = null;
        BigDecimal volumeRatio = null;
        try {
            List<BarDaily> bench = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                    .eq(BarDaily::getCode, "000300")
                    .orderByDesc(BarDaily::getTradeDate)
                    .last("limit 80"));
            bench.sort((a, b) -> a.getTradeDate().compareTo(b.getTradeDate()));
            rs20 = relativeStrengthPct(bars, bench, 20);
            rs60 = relativeStrengthPct(bars, bench, 60);
            volumeRatio = calcVolumeRatio(bars, 20);
        } catch (Exception ignored) {
            // 相对强度失败不影响详情
        }

        return StockDetailResp.builder()
                .basic(basic)
                .bars(bars)
                .rs20VsHs300(rs20)
                .rs60VsHs300(rs60)
                .volumeRatio(volumeRatio)
                .note("基本信息：新浪快照 + 东财/腾讯估值 + 东财F10行业；K线来自本地日线库。过去表现不代表未来收益。")
                .build();
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

    private BigDecimal relativeStrengthPct(List<BarDaily> stock, List<BarDaily> bench, int lookback) {
        BigDecimal stockRet = periodReturn(stock, lookback);
        BigDecimal benchRet = periodReturn(bench, lookback);
        if (Objects.isNull(stockRet) || Objects.isNull(benchRet)) {
            return null;
        }
        return stockRet.subtract(benchRet).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal periodReturn(List<BarDaily> bars, int lookback) {
        if (bars == null || bars.size() <= lookback) {
            return null;
        }
        BigDecimal end = bars.get(bars.size() - 1).getClosePrice();
        BigDecimal start = bars.get(bars.size() - 1 - lookback).getClosePrice();
        if (Objects.isNull(end) || Objects.isNull(start) || start.signum() <= 0) {
            return null;
        }
        return end.subtract(start).divide(start, 6, RoundingMode.HALF_UP);
    }

    /**
     * 按代码/名称搜索
     *
     * @param keyword 关键词
     * @param limit   条数
     * @return 结果
     */
    @Override
    public List<StockSearchItem> search(String keyword, Integer limit) {
        if (StringUtils.isBlank(keyword)) {
            return Collections.emptyList();
        }
        String q = keyword.trim();
        int size = Objects.isNull(limit) ? 15 : Math.max(1, Math.min(limit, 50));
        Map<String, StockSearchItem> map = new LinkedHashMap<>();

        List<Watchlist> watchlists = watchlistMapper.selectList(Wrappers.<Watchlist>lambdaQuery()
                .and(w -> w.like(Watchlist::getCode, q).or().like(Watchlist::getName, q))
                .last("limit " + size));
        for (Watchlist item : watchlists) {
            map.putIfAbsent(item.getCode(), StockSearchItem.builder()
                    .code(item.getCode())
                    .name(item.getName())
                    .market(item.getMarket())
                    .source("watchlist")
                    .build());
        }

        List<StockBasic> basics = stockBasicMapper.selectList(Wrappers.<StockBasic>lambdaQuery()
                .and(w -> w.like(StockBasic::getCode, q).or().like(StockBasic::getName, q))
                .last("limit " + size));
        for (StockBasic basic : basics) {
            StockSearchItem existing = map.get(basic.getCode());
            if (Objects.isNull(existing)) {
                map.put(basic.getCode(), StockSearchItem.builder()
                        .code(basic.getCode())
                        .name(basic.getName())
                        .market(basic.getMarket())
                        .latestPrice(basic.getLatestPrice())
                        .source("stock_basic")
                        .build());
            } else {
                if (StringUtils.isBlank(existing.getName())) {
                    existing.setName(basic.getName());
                }
                existing.setLatestPrice(basic.getLatestPrice());
            }
        }

        // 纯 6 位代码且本地无记录时，给一条可直达项
        String pure = MarketCodeUtils.normalizeCode(q);
        if (pure != null && pure.matches("\\d{6}") && !map.containsKey(pure)) {
            map.put(pure, StockSearchItem.builder()
                    .code(pure)
                    .name(null)
                    .market(MarketCodeUtils.resolveMarket(pure))
                    .source("code")
                    .build());
        }

        List<StockSearchItem> result = new ArrayList<>();
        for (StockSearchItem item : map.values()) {
            result.add(item);
            if (result.size() >= size) {
                break;
            }
        }
        return result;
    }
}
