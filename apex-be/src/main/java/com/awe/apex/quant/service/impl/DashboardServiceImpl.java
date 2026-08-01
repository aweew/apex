package com.awe.apex.quant.service.impl;

import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.DashboardResp;
import com.awe.apex.quant.domain.dto.EquityPointResp;
import com.awe.apex.quant.domain.dto.IndustryPnlResp;
import com.awe.apex.quant.domain.dto.PaperMetricsResp;
import com.awe.apex.quant.domain.dto.RiskOverviewResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.PaperAccount;
import com.awe.apex.quant.domain.entity.PaperOrder;
import com.awe.apex.quant.domain.entity.PaperPosition;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.StrategySignalEntity;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.StrategySignalMapper;
import com.awe.apex.quant.paper.PaperEquityCalculator;
import com.awe.apex.quant.service.IDailyActionService;
import com.awe.apex.quant.service.IDashboardService;
import com.awe.apex.quant.service.IPaperService;
import com.awe.apex.quant.service.IRiskService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 仪表盘实现
 */
@Service
public class DashboardServiceImpl implements IDashboardService {

    @Resource
    private IPaperService paperService;

    @Resource
    private IRiskService riskService;

    @Resource
    private IDailyActionService dailyActionService;

    @Resource
    private StrategySignalMapper strategySignalMapper;

    @Resource
    private StockBasicMapper stockBasicMapper;

    @Resource
    private BarDailyMapper barDailyMapper;

    @Override
    public DashboardResp overview(Long accountId) {
        PaperAccount account = paperService.defaultAccount();
        Long id = Objects.nonNull(accountId) ? accountId : account.getId();
        RiskOverviewResp risk = riskService.overview(id);
        List<PaperPosition> positions = paperService.listPositions(id);
        List<PaperOrder> orders = paperService.listOrders(id);
        List<StrategySignalEntity> signals = strategySignalMapper.selectList(Wrappers.<StrategySignalEntity>lambdaQuery()
                .ge(StrategySignalEntity::getSignalDate, LocalDate.now().minusDays(5))
                .orderByDesc(StrategySignalEntity::getId)
                .last("limit 50"));

        BigDecimal unrealized = BigDecimal.ZERO;
        for (PaperPosition position : positions) {
            if (Objects.nonNull(position.getPnl())) {
                unrealized = unrealized.add(position.getPnl());
            }
        }
        BigDecimal initCash = Objects.nonNull(account.getInitCash()) ? account.getInitCash() : BigDecimal.valueOf(1000000);
        BigDecimal totalAsset = risk.getTotalAsset();
        BigDecimal totalReturn = initCash.signum() == 0 ? BigDecimal.ZERO
                : totalAsset.subtract(initCash).divide(initCash, 4, RoundingMode.HALF_UP);

        TradeStats tradeStats = calcTradeStats(orders);
        BigDecimal turnoverRate = initCash.signum() == 0 ? BigDecimal.ZERO
                : tradeStats.totalTurnover.divide(initCash, 4, RoundingMode.HALF_UP);
        BigDecimal totalFee = BigDecimal.ZERO;
        for (PaperOrder order : orders) {
            if (Objects.nonNull(order.getFee())) {
                totalFee = totalFee.add(order.getFee());
            }
        }
        BigDecimal feeRate = tradeStats.totalTurnover.signum() == 0 ? BigDecimal.ZERO
                : totalFee.divide(tradeStats.totalTurnover, 6, RoundingMode.HALF_UP);

        PaperEquityCalculator.ReplayResult replay = buildEquityReplay(account, orders, totalAsset);

        PaperMetricsResp metrics = PaperMetricsResp.builder()
                .initCash(initCash)
                .totalAsset(totalAsset)
                .totalReturn(totalReturn)
                .orderCount(orders.size())
                .positionCount(positions.size())
                .unrealizedPnl(unrealized)
                .realizedPnl(tradeStats.realizedPnl)
                .winRate(tradeStats.winRate)
                .closedTradeCount(tradeStats.closedTradeCount)
                .totalTurnover(tradeStats.totalTurnover)
                .turnoverRate(turnoverRate)
                .maxDrawdown(replay.getMaxDrawdown())
                .sharpe(replay.getSharpe())
                .dailyVar95(replay.getDailyVar95())
                .dailyCvar95(replay.getDailyCvar95())
                .calmar(replay.getCalmar())
                .avgHoldDays(tradeStats.avgHoldDays)
                .winStreak(tradeStats.winStreak)
                .lossStreak(tradeStats.lossStreak)
                .drawdownRecoveryDays(replay.getDrawdownRecoveryDays())
                .underwaterRatio(replay.getUnderwaterRatio())
                .rollingSharpe20(calcRollingSharpe(replay.getPoints(), 20))
                .profitFactor(tradeStats.profitFactor)
                .payoffRatio(tradeStats.payoffRatio)
                .expectancy(tradeStats.expectancy)
                .sortino(calcSortino(replay.getPoints()))
                .ulcerIndex(calcUlcerIndex(replay.getPoints()))
                .totalFee(totalFee.setScale(2, RoundingMode.HALF_UP))
                .feeRate(feeRate)
                .omega(calcOmega(replay.getPoints()))
                .maxLosingDays(calcMaxLosingDays(replay.getPoints()))
                .cashDrag(Objects.nonNull(risk.getPositionRatio())
                        ? BigDecimal.ONE.subtract(risk.getPositionRatio()).max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP)
                        : BigDecimal.ONE)
                .build();

        return DashboardResp.builder()
                .risk(risk)
                .positions(positions)
                .recentSignals(signals)
                .todayActions(dailyActionService.listByDate(LocalDate.now()))
                .paperMetrics(metrics)
                .equityCurve(replay.getPoints())
                .industryPnls(buildIndustryPnls(positions, totalAsset))
                .build();
    }

    private TradeStats calcTradeStats(List<PaperOrder> orders) {
        BigDecimal totalTurnover = BigDecimal.ZERO;
        Map<String, List<Lot>> openLots = new HashMap<>();
        BigDecimal realized = BigDecimal.ZERO;
        BigDecimal grossWin = BigDecimal.ZERO;
        BigDecimal grossLoss = BigDecimal.ZERO;
        int wins = 0;
        int closed = 0;
        long holdDaysSum = 0;
        List<Boolean> closedWins = new ArrayList<>();
        List<PaperOrder> sorted = new ArrayList<>(orders);
        sorted.sort((a, b) -> {
            int byDate = a.getTradeDate().compareTo(b.getTradeDate());
            if (byDate != 0) {
                return byDate;
            }
            return Long.compare(a.getId(), b.getId());
        });
        for (PaperOrder order : sorted) {
            if (Objects.nonNull(order.getAmount())) {
                totalTurnover = totalTurnover.add(order.getAmount());
            }
            String code = order.getCode();
            int qty = Objects.nonNull(order.getQuantity()) ? order.getQuantity() : 0;
            if (qty <= 0 || Objects.isNull(order.getPrice())) {
                continue;
            }
            if ("BUY".equalsIgnoreCase(order.getSide())) {
                openLots.computeIfAbsent(code, k -> new ArrayList<>())
                        .add(new Lot(qty, order.getPrice(), order.getTradeDate()));
                continue;
            }
            if (!"SELL".equalsIgnoreCase(order.getSide())) {
                continue;
            }
            List<Lot> lots = openLots.computeIfAbsent(code, k -> new ArrayList<>());
            int remain = qty;
            BigDecimal sellFeeShare = Objects.nonNull(order.getFee()) ? order.getFee() : BigDecimal.ZERO;
            while (remain > 0 && !lots.isEmpty()) {
                Lot lot = lots.get(0);
                int matched = Math.min(remain, lot.qty);
                BigDecimal pnl = order.getPrice().subtract(lot.cost)
                        .multiply(BigDecimal.valueOf(matched));
                if (qty > 0) {
                    pnl = pnl.subtract(sellFeeShare.multiply(BigDecimal.valueOf(matched))
                            .divide(BigDecimal.valueOf(qty), 6, RoundingMode.HALF_UP));
                }
                realized = realized.add(pnl);
                closed++;
                boolean win = pnl.signum() >= 0;
                if (win) {
                    wins++;
                    grossWin = grossWin.add(pnl);
                } else {
                    grossLoss = grossLoss.add(pnl.abs());
                }
                closedWins.add(win);
                if (Objects.nonNull(lot.openDate) && Objects.nonNull(order.getTradeDate())) {
                    holdDaysSum += Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(lot.openDate, order.getTradeDate()));
                }
                lot.qty -= matched;
                remain -= matched;
                if (lot.qty <= 0) {
                    lots.remove(0);
                }
            }
        }
        BigDecimal winRate = closed == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(closed), 4, RoundingMode.HALF_UP);
        BigDecimal avgHold = closed == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(holdDaysSum).divide(BigDecimal.valueOf(closed), 2, RoundingMode.HALF_UP);
        int winStreak = 0;
        int lossStreak = 0;
        for (int i = closedWins.size() - 1; i >= 0; i--) {
            if (closedWins.get(i)) {
                if (lossStreak > 0) {
                    break;
                }
                winStreak++;
            } else {
                if (winStreak > 0) {
                    break;
                }
                lossStreak++;
            }
        }
        BigDecimal profitFactor = grossLoss.signum() == 0
                ? (grossWin.signum() > 0 ? BigDecimal.valueOf(99) : BigDecimal.ZERO)
                : grossWin.divide(grossLoss, 4, RoundingMode.HALF_UP);
        int lossCount = closed - wins;
        BigDecimal avgWin = wins == 0 ? BigDecimal.ZERO
                : grossWin.divide(BigDecimal.valueOf(wins), 6, RoundingMode.HALF_UP);
        BigDecimal avgLoss = lossCount == 0 ? BigDecimal.ZERO
                : grossLoss.divide(BigDecimal.valueOf(lossCount), 6, RoundingMode.HALF_UP);
        BigDecimal payoff = avgLoss.signum() == 0
                ? (avgWin.signum() > 0 ? BigDecimal.valueOf(99) : BigDecimal.ZERO)
                : avgWin.divide(avgLoss, 4, RoundingMode.HALF_UP);
        // E = p*W - (1-p)*L
        BigDecimal expectancy = closed == 0 ? BigDecimal.ZERO
                : winRate.multiply(avgWin)
                .subtract(BigDecimal.ONE.subtract(winRate).multiply(avgLoss))
                .setScale(2, RoundingMode.HALF_UP);
        return new TradeStats(realized.setScale(2, RoundingMode.HALF_UP), winRate, closed,
                totalTurnover.setScale(2, RoundingMode.HALF_UP), avgHold, winStreak, lossStreak,
                profitFactor, payoff, expectancy);
    }

    private Integer calcMaxLosingDays(List<EquityPointResp> points) {
        if (Objects.isNull(points) || points.size() < 2) {
            return 0;
        }
        int max = 0;
        int cur = 0;
        for (int i = 1; i < points.size(); i++) {
            BigDecimal prev = points.get(i - 1).getEquity();
            BigDecimal curr = points.get(i).getEquity();
            if (Objects.nonNull(prev) && Objects.nonNull(curr) && curr.compareTo(prev) < 0) {
                cur++;
                if (cur > max) {
                    max = cur;
                }
            } else {
                cur = 0;
            }
        }
        return max;
    }

    private BigDecimal calcOmega(List<EquityPointResp> points) {
        if (Objects.isNull(points) || points.size() < 6) {
            return BigDecimal.ZERO;
        }
        BigDecimal gains = BigDecimal.ZERO;
        BigDecimal losses = BigDecimal.ZERO;
        for (int i = 1; i < points.size(); i++) {
            BigDecimal prev = points.get(i - 1).getEquity();
            BigDecimal curr = points.get(i).getEquity();
            if (Objects.isNull(prev) || prev.signum() <= 0 || Objects.isNull(curr)) {
                continue;
            }
            BigDecimal r = curr.subtract(prev).divide(prev, 8, RoundingMode.HALF_UP);
            if (r.signum() >= 0) {
                gains = gains.add(r);
            } else {
                losses = losses.add(r.abs());
            }
        }
        if (losses.signum() == 0) {
            return gains.signum() > 0 ? BigDecimal.valueOf(99) : BigDecimal.ZERO;
        }
        return gains.divide(losses, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal calcUlcerIndex(List<EquityPointResp> points) {
        if (Objects.isNull(points) || points.size() < 3) {
            return BigDecimal.ZERO;
        }
        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal sumSq = BigDecimal.ZERO;
        int n = 0;
        for (EquityPointResp point : points) {
            BigDecimal eq = point.getEquity();
            if (Objects.isNull(eq)) {
                continue;
            }
            if (eq.compareTo(peak) > 0) {
                peak = eq;
            }
            if (peak.signum() > 0) {
                BigDecimal ddPct = peak.subtract(eq).divide(peak, 8, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                sumSq = sumSq.add(ddPct.multiply(ddPct));
                n++;
            }
        }
        if (n == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(Math.sqrt(sumSq.divide(BigDecimal.valueOf(n), 8, RoundingMode.HALF_UP).doubleValue()))
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal calcSortino(List<EquityPointResp> points) {
        if (Objects.isNull(points) || points.size() < 6) {
            return BigDecimal.ZERO;
        }
        List<BigDecimal> rets = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            BigDecimal prev = points.get(i - 1).getEquity();
            BigDecimal curr = points.get(i).getEquity();
            if (Objects.nonNull(prev) && prev.signum() > 0 && Objects.nonNull(curr)) {
                rets.add(curr.subtract(prev).divide(prev, 8, RoundingMode.HALF_UP));
            }
        }
        if (rets.size() < 5) {
            return BigDecimal.ZERO;
        }
        BigDecimal mean = rets.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(rets.size()), 8, RoundingMode.HALF_UP);
        BigDecimal downside = BigDecimal.ZERO;
        int n = 0;
        for (BigDecimal r : rets) {
            if (r.signum() < 0) {
                downside = downside.add(r.multiply(r));
                n++;
            }
        }
        if (n == 0) {
            return mean.signum() > 0 ? BigDecimal.valueOf(99) : BigDecimal.ZERO;
        }
        double dd = Math.sqrt(downside.divide(BigDecimal.valueOf(n), 8, RoundingMode.HALF_UP).doubleValue());
        if (dd == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(mean.doubleValue() / dd * Math.sqrt(252)).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal calcRollingSharpe(List<EquityPointResp> points, int window) {
        if (Objects.isNull(points) || points.size() < window + 1) {
            return BigDecimal.ZERO;
        }
        List<BigDecimal> rets = new ArrayList<>();
        int from = points.size() - window - 1;
        for (int i = from + 1; i < points.size(); i++) {
            BigDecimal prev = points.get(i - 1).getEquity();
            BigDecimal curr = points.get(i).getEquity();
            if (Objects.nonNull(prev) && prev.signum() > 0 && Objects.nonNull(curr)) {
                rets.add(curr.subtract(prev).divide(prev, 8, RoundingMode.HALF_UP));
            }
        }
        if (rets.size() < 5) {
            return BigDecimal.ZERO;
        }
        BigDecimal mean = rets.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(rets.size()), 8, RoundingMode.HALF_UP);
        BigDecimal variance = BigDecimal.ZERO;
        for (BigDecimal r : rets) {
            BigDecimal d = r.subtract(mean);
            variance = variance.add(d.multiply(d));
        }
        variance = variance.divide(BigDecimal.valueOf(rets.size() - 1), 8, RoundingMode.HALF_UP);
        if (variance.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        double std = Math.sqrt(variance.doubleValue());
        if (std == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(mean.doubleValue() / std * Math.sqrt(252)).setScale(4, RoundingMode.HALF_UP);
    }

    private static final class Lot {
        private int qty;
        private final BigDecimal cost;
        private final LocalDate openDate;

        private Lot(int qty, BigDecimal cost, LocalDate openDate) {
            this.qty = qty;
            this.cost = cost;
            this.openDate = openDate;
        }
    }

    private static final class TradeStats {
        private final BigDecimal realizedPnl;
        private final BigDecimal winRate;
        private final int closedTradeCount;
        private final BigDecimal totalTurnover;
        private final BigDecimal avgHoldDays;
        private final int winStreak;
        private final int lossStreak;
        private final BigDecimal profitFactor;
        private final BigDecimal payoffRatio;
        private final BigDecimal expectancy;

        private TradeStats(BigDecimal realizedPnl, BigDecimal winRate, int closedTradeCount,
                           BigDecimal totalTurnover, BigDecimal avgHoldDays, int winStreak, int lossStreak,
                           BigDecimal profitFactor, BigDecimal payoffRatio, BigDecimal expectancy) {
            this.realizedPnl = realizedPnl;
            this.winRate = winRate;
            this.closedTradeCount = closedTradeCount;
            this.totalTurnover = totalTurnover;
            this.avgHoldDays = avgHoldDays;
            this.winStreak = winStreak;
            this.lossStreak = lossStreak;
            this.profitFactor = profitFactor;
            this.payoffRatio = payoffRatio;
            this.expectancy = expectancy;
        }
    }

    private List<IndustryPnlResp> buildIndustryPnls(List<PaperPosition> positions, BigDecimal totalAsset) {
        Map<String, BigDecimal> mvMap = new HashMap<>();
        Map<String, BigDecimal> pnlMap = new HashMap<>();
        for (PaperPosition position : positions) {
            String industry = "未分类";
            StockBasic basic = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                    .eq(StockBasic::getCode, position.getCode())
                    .last("limit 1"));
            if (Objects.nonNull(basic) && StringUtils.isNotBlank(basic.getIndustry())) {
                industry = basic.getIndustry();
            }
            BigDecimal mv = Objects.nonNull(position.getMarketValue()) ? position.getMarketValue() : BigDecimal.ZERO;
            BigDecimal pnl = Objects.nonNull(position.getPnl()) ? position.getPnl() : BigDecimal.ZERO;
            mvMap.merge(industry, mv, BigDecimal::add);
            pnlMap.merge(industry, pnl, BigDecimal::add);
        }
        List<IndustryPnlResp> list = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : mvMap.entrySet()) {
            BigDecimal weight = totalAsset.signum() == 0 ? BigDecimal.ZERO
                    : entry.getValue().divide(totalAsset, 4, RoundingMode.HALF_UP);
            list.add(IndustryPnlResp.builder()
                    .industry(entry.getKey())
                    .marketValue(entry.getValue())
                    .pnl(pnlMap.getOrDefault(entry.getKey(), BigDecimal.ZERO))
                    .weight(weight)
                    .build());
        }
        list.sort(Comparator.comparing(IndustryPnlResp::getMarketValue).reversed());
        return list;
    }

    private PaperEquityCalculator.ReplayResult buildEquityReplay(PaperAccount account,
                                                                List<PaperOrder> orders,
                                                                BigDecimal terminalEquity) {
        BigDecimal initCash = Objects.nonNull(account.getInitCash()) ? account.getInitCash() : BigDecimal.valueOf(1000000);
        if (orders.isEmpty()) {
            return PaperEquityCalculator.replay(initCash, orders, Map.of(), terminalEquity);
        }
        Set<String> codes = new HashSet<>();
        LocalDate minDate = null;
        for (PaperOrder order : orders) {
            if (StringUtils.isNotBlank(order.getCode())) {
                codes.add(order.getCode());
            }
            if (Objects.nonNull(order.getTradeDate()) && (Objects.isNull(minDate) || order.getTradeDate().isBefore(minDate))) {
                minDate = order.getTradeDate();
            }
        }
        Map<String, Map<LocalDate, BigDecimal>> closesByCode = new HashMap<>();
        if (!codes.isEmpty() && Objects.nonNull(minDate)) {
            List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                    .in(BarDaily::getCode, codes)
                    .ge(BarDaily::getTradeDate, minDate)
                    .orderByAsc(BarDaily::getTradeDate));
            for (BarDaily bar : bars) {
                if (Objects.isNull(bar.getClosePrice())) {
                    continue;
                }
                closesByCode.computeIfAbsent(bar.getCode(), k -> new HashMap<>())
                        .put(bar.getTradeDate(), bar.getClosePrice());
            }
        }
        return PaperEquityCalculator.replay(initCash, orders, closesByCode, terminalEquity);
    }
}
