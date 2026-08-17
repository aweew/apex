package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.IndustryHeatItem;
import com.awe.apex.quant.domain.dto.MarketBoardResp;
import com.awe.apex.quant.domain.dto.MarketBreadthResp;
import com.awe.apex.quant.domain.dto.VolRegimeResp;
import com.awe.apex.quant.domain.dto.WatchlistResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.StrategySignalEntity;
import com.awe.apex.quant.domain.entity.UniverseSnapshot;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.StrategySignalMapper;
import com.awe.apex.quant.service.IUniverseService;
import com.awe.apex.quant.service.IWatchlistService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 行情看板
 */
@RestController
@RequestMapping("/api/market/board")
public class MarketBoardController {

    @Resource
    private IWatchlistService watchlistService;

    @Resource
    private IUniverseService universeService;

    @Resource
    private StrategySignalMapper strategySignalMapper;

    @Resource
    private BarDailyMapper barDailyMapper;

    @Resource
    private ApexUserContext userContext;

    /**
     * 自选涨跌榜 + 行业热力 + 信号统计
     *
     * @param groupName 分组
     * @param limit     涨跌榜条数
     * @return 看板
     */
    @GetMapping
    public Result<MarketBoardResp> board(@RequestParam(required = false) String groupName,
                                         @RequestParam(defaultValue = "10") Integer limit) {
        int size = Objects.isNull(limit) ? 10 : Math.max(3, Math.min(limit, 30));
        List<WatchlistResp> list = watchlistService.listWatchlist(groupName);
        List<WatchlistResp> withPct = new ArrayList<>();
        for (WatchlistResp row : list) {
            if (Objects.nonNull(row.getPctChg())) {
                withPct.add(row);
            }
        }
        withPct.sort(Comparator.comparing(WatchlistResp::getPctChg).reversed());
        List<WatchlistResp> gainers = new ArrayList<>();
        for (int i = 0; i < Math.min(size, withPct.size()); i++) {
            gainers.add(withPct.get(i));
        }
        List<WatchlistResp> losers = new ArrayList<>();
        for (int i = withPct.size() - 1; i >= 0 && losers.size() < size; i--) {
            losers.add(withPct.get(i));
        }

        Map<String, List<BigDecimal>> industryPct = new HashMap<>();
        for (WatchlistResp row : list) {
            if (Objects.isNull(row.getPctChg())) {
                continue;
            }
            String industry = StringUtils.isNotBlank(row.getIndustry()) ? row.getIndustry() : "未分类";
            industryPct.computeIfAbsent(industry, k -> new ArrayList<>()).add(row.getPctChg());
        }
        List<IndustryHeatItem> heat = new ArrayList<>();
        for (Map.Entry<String, List<BigDecimal>> entry : industryPct.entrySet()) {
            List<BigDecimal> pcts = entry.getValue();
            BigDecimal sum = BigDecimal.ZERO;
            int up = 0;
            int down = 0;
            for (BigDecimal pct : pcts) {
                sum = sum.add(pct);
                if (pct.signum() > 0) {
                    up++;
                } else if (pct.signum() < 0) {
                    down++;
                }
            }
            heat.add(IndustryHeatItem.builder()
                    .industry(entry.getKey())
                    .stockCount(pcts.size())
                    .avgPctChg(sum.divide(BigDecimal.valueOf(pcts.size()), 4, RoundingMode.HALF_UP))
                    .upCount(up)
                    .downCount(down)
                    .build());
        }
        heat.sort(Comparator.comparing(IndustryHeatItem::getAvgPctChg).reversed());
        if (heat.size() > 15) {
            heat = heat.subList(0, 15);
        }

        LocalDate since = LocalDate.now().minusDays(5);
        Long buyCount = strategySignalMapper.selectCount(Wrappers.<StrategySignalEntity>lambdaQuery()
                .eq(StrategySignalEntity::getUserId, userContext.currentUserId())
                .ge(StrategySignalEntity::getSignalDate, since)
                .eq(StrategySignalEntity::getSide, "BUY"));
        Long sellCount = strategySignalMapper.selectCount(Wrappers.<StrategySignalEntity>lambdaQuery()
                .eq(StrategySignalEntity::getUserId, userContext.currentUserId())
                .ge(StrategySignalEntity::getSignalDate, since)
                .eq(StrategySignalEntity::getSide, "SELL"));
        List<UniverseSnapshot> universe = universeService.latest();

        int advance = 0;
        int decline = 0;
        int flat = 0;
        int above20 = 0;
        int with20 = 0;
        for (WatchlistResp row : list) {
            if (Objects.nonNull(row.getPctChg())) {
                if (row.getPctChg().signum() > 0) {
                    advance++;
                } else if (row.getPctChg().signum() < 0) {
                    decline++;
                } else {
                    flat++;
                }
            }
            if (Objects.nonNull(row.getPctChg20())) {
                with20++;
                if (row.getPctChg20().signum() > 0) {
                    above20++;
                }
            }
        }
        BigDecimal adRatio = decline == 0
                ? BigDecimal.valueOf(advance)
                : BigDecimal.valueOf(advance).divide(BigDecimal.valueOf(decline), 4, RoundingMode.HALF_UP);
        BigDecimal above20Pct = with20 == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(above20).divide(BigDecimal.valueOf(with20), 4, RoundingMode.HALF_UP);
        MarketBreadthResp breadth = MarketBreadthResp.builder()
                .sampleCount(list.size())
                .advanceCount(advance)
                .declineCount(decline)
                .flatCount(flat)
                .advanceDeclineRatio(adRatio)
                .above20DayPct(above20Pct)
                .message("涨 " + advance + " / 跌 " + decline + " · A/D " + adRatio + " · 20日上涨占比 "
                        + above20Pct.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP) + "%")
                .build();

        return Result.success(MarketBoardResp.builder()
                .gainers(gainers)
                .losers(losers)
                .industryHeat(heat)
                .breadth(breadth)
                .volRegime(buildVolRegime("000300"))
                .buySignalCount(Objects.nonNull(buyCount) ? buyCount.intValue() : 0)
                .sellSignalCount(Objects.nonNull(sellCount) ? sellCount.intValue() : 0)
                .universeCount(universe.size())
                .build());
    }

    private VolRegimeResp buildVolRegime(String code) {
        List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, code)
                .ge(BarDaily::getTradeDate, LocalDate.now().minusDays(400))
                .orderByAsc(BarDaily::getTradeDate));
        if (bars.size() < 40) {
            return VolRegimeResp.builder()
                    .code(code)
                    .realizedVol20(BigDecimal.ZERO)
                    .volPercentile(BigDecimal.ZERO)
                    .regime("MID")
                    .message("基准日线不足，无法估计波动体制")
                    .build();
        }
        List<BigDecimal> closes = new ArrayList<>();
        for (BarDaily bar : bars) {
            if (Objects.nonNull(bar.getClosePrice())) {
                closes.add(bar.getClosePrice());
            }
        }
        List<Double> rets = new ArrayList<>();
        for (int i = 1; i < closes.size(); i++) {
            BigDecimal prev = closes.get(i - 1);
            BigDecimal curr = closes.get(i);
            if (prev.signum() > 0) {
                rets.add(curr.subtract(prev).divide(prev, 8, RoundingMode.HALF_UP).doubleValue());
            }
        }
        if (rets.size() < 30) {
            return VolRegimeResp.builder().code(code).regime("MID").message("收益样本不足").build();
        }
        List<Double> rollingVol = new ArrayList<>();
        int win = 20;
        for (int i = win; i <= rets.size(); i++) {
            List<Double> slice = rets.subList(i - win, i);
            double mean = slice.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double var = 0;
            for (double r : slice) {
                var += (r - mean) * (r - mean);
            }
            var /= (slice.size() - 1);
            rollingVol.add(Math.sqrt(var) * Math.sqrt(252));
        }
        double current = rollingVol.get(rollingVol.size() - 1);
        int below = 0;
        for (double v : rollingVol) {
            if (v <= current) {
                below++;
            }
        }
        BigDecimal pct = BigDecimal.valueOf(below * 1.0 / rollingVol.size()).setScale(4, RoundingMode.HALF_UP);
        String regime = "MID";
        if (pct.compareTo(new BigDecimal("0.7")) >= 0) {
            regime = "HIGH";
        } else if (pct.compareTo(new BigDecimal("0.3")) <= 0) {
            regime = "LOW";
        }
        return VolRegimeResp.builder()
                .code(code)
                .realizedVol20(BigDecimal.valueOf(current).setScale(4, RoundingMode.HALF_UP))
                .volPercentile(pct)
                .regime(regime)
                .message("沪深300 20日波动 " + BigDecimal.valueOf(current * 100).setScale(1, RoundingMode.HALF_UP)
                        + "% · 一年分位 " + pct.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP)
                        + "% · " + regime)
                .build();
    }
}
