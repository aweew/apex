package com.awe.apex.quant.paper;

import com.awe.apex.quant.domain.dto.EquityPointResp;
import com.awe.apex.quant.domain.entity.PaperOrder;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * 模拟盘权益回放：按日线收盘价标记市值（MTM）
 */
public final class PaperEquityCalculator {

    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);

    private PaperEquityCalculator() {
    }

    /**
     * 回放结果
     */
    public static final class ReplayResult {
        private final List<EquityPointResp> points;
        private final BigDecimal maxDrawdown;
        private final BigDecimal sharpe;
        private final BigDecimal dailyVar95;
        private final BigDecimal dailyCvar95;
        private final BigDecimal calmar;
        private final Integer drawdownRecoveryDays;
        private final BigDecimal underwaterRatio;

        public ReplayResult(List<EquityPointResp> points, BigDecimal maxDrawdown, BigDecimal sharpe,
                            BigDecimal dailyVar95, BigDecimal dailyCvar95, BigDecimal calmar,
                            Integer drawdownRecoveryDays, BigDecimal underwaterRatio) {
            this.points = points;
            this.maxDrawdown = maxDrawdown;
            this.sharpe = sharpe;
            this.dailyVar95 = dailyVar95;
            this.dailyCvar95 = dailyCvar95;
            this.calmar = calmar;
            this.drawdownRecoveryDays = drawdownRecoveryDays;
            this.underwaterRatio = underwaterRatio;
        }

        public List<EquityPointResp> getPoints() {
            return points;
        }

        public BigDecimal getMaxDrawdown() {
            return maxDrawdown;
        }

        public BigDecimal getSharpe() {
            return sharpe;
        }

        public BigDecimal getDailyVar95() {
            return dailyVar95;
        }

        public BigDecimal getDailyCvar95() {
            return dailyCvar95;
        }

        public BigDecimal getCalmar() {
            return calmar;
        }

        public Integer getDrawdownRecoveryDays() {
            return drawdownRecoveryDays;
        }

        public BigDecimal getUnderwaterRatio() {
            return underwaterRatio;
        }
    }

    /**
     * 按订单与日线收盘回放权益曲线
     *
     * @param initCash       初始资金
     * @param orders         成交订单
     * @param closesByCode   code -> (tradeDate -> close)
     * @param terminalEquity 终点总资产（含最新浮盈），可空
     * @return 回放结果
     */
    public static ReplayResult replay(BigDecimal initCash,
                                      List<PaperOrder> orders,
                                      Map<String, Map<LocalDate, BigDecimal>> closesByCode,
                                      BigDecimal terminalEquity) {
        BigDecimal cashBase = Objects.nonNull(initCash) ? initCash : BigDecimal.valueOf(1000000);
        List<EquityPointResp> points = new ArrayList<>();
        if (orders == null || orders.isEmpty()) {
            BigDecimal eq = Objects.nonNull(terminalEquity) ? terminalEquity : cashBase;
            points.add(EquityPointResp.builder().tradeDate(LocalDate.now()).equity(eq.setScale(2, RoundingMode.HALF_UP)).build());
            return new ReplayResult(points, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO);
        }

        List<PaperOrder> sorted = new ArrayList<>(orders);
        sorted.sort((a, b) -> {
            int byDate = a.getTradeDate().compareTo(b.getTradeDate());
            if (byDate != 0) {
                return byDate;
            }
            return Long.compare(a.getId(), b.getId());
        });

        LocalDate minDate = sorted.get(0).getTradeDate();
        TreeSet<LocalDate> dates = new TreeSet<>();
        for (Map<LocalDate, BigDecimal> closes : closesByCode.values()) {
            for (LocalDate d : closes.keySet()) {
                if (!d.isBefore(minDate)) {
                    dates.add(d);
                }
            }
        }
        for (PaperOrder order : sorted) {
            dates.add(order.getTradeDate());
        }
        if (dates.isEmpty()) {
            dates.add(minDate);
        }

        Map<LocalDate, List<PaperOrder>> ordersByDay = new HashMap<>();
        for (PaperOrder order : sorted) {
            ordersByDay.computeIfAbsent(order.getTradeDate(), k -> new ArrayList<>()).add(order);
        }

        BigDecimal cash = cashBase;
        Map<String, Integer> holdings = new HashMap<>();
        Map<String, BigDecimal> lastClose = new HashMap<>();
        List<BigDecimal> equities = new ArrayList<>();

        for (LocalDate date : dates) {
            List<PaperOrder> dayOrders = ordersByDay.get(date);
            if (dayOrders != null) {
                for (PaperOrder order : dayOrders) {
                    BigDecimal amount = Objects.nonNull(order.getAmount()) ? order.getAmount() : BigDecimal.ZERO;
                    BigDecimal fee = Objects.nonNull(order.getFee()) ? order.getFee() : BigDecimal.ZERO;
                    int qty = Objects.nonNull(order.getQuantity()) ? order.getQuantity() : 0;
                    String code = order.getCode();
                    if ("BUY".equalsIgnoreCase(order.getSide())) {
                        cash = cash.subtract(amount.add(fee));
                        holdings.merge(code, qty, Integer::sum);
                    } else if ("SELL".equalsIgnoreCase(order.getSide())) {
                        cash = cash.add(amount).subtract(fee);
                        holdings.merge(code, -qty, Integer::sum);
                    }
                }
            }
            BigDecimal mv = BigDecimal.ZERO;
            for (Map.Entry<String, Integer> entry : holdings.entrySet()) {
                if (entry.getValue() == null || entry.getValue() <= 0) {
                    continue;
                }
                Map<LocalDate, BigDecimal> closes = closesByCode.get(entry.getKey());
                BigDecimal px = null;
                if (closes != null) {
                    px = closes.get(date);
                }
                if (px != null) {
                    lastClose.put(entry.getKey(), px);
                } else {
                    px = lastClose.get(entry.getKey());
                }
                if (px != null) {
                    mv = mv.add(px.multiply(BigDecimal.valueOf(entry.getValue())));
                }
            }
            BigDecimal equity = cash.add(mv).setScale(2, RoundingMode.HALF_UP);
            points.add(EquityPointResp.builder().tradeDate(date).equity(equity).build());
            equities.add(equity);
        }

        if (Objects.nonNull(terminalEquity) && !points.isEmpty()) {
            EquityPointResp last = points.get(points.size() - 1);
            if (!last.getTradeDate().equals(LocalDate.now())) {
                points.add(EquityPointResp.builder()
                        .tradeDate(LocalDate.now())
                        .equity(terminalEquity.setScale(2, RoundingMode.HALF_UP))
                        .build());
                equities.add(terminalEquity.setScale(2, RoundingMode.HALF_UP));
            } else {
                last.setEquity(terminalEquity.setScale(2, RoundingMode.HALF_UP));
                equities.set(equities.size() - 1, last.getEquity());
            }
        }
        BigDecimal maxDd = calcMaxDrawdown(equities);
        BigDecimal sharpe = calcSharpe(equities);
        BigDecimal var95 = calcDailyVar95(equities);
        BigDecimal cvar95 = calcDailyCvar95(equities);
        BigDecimal calmar = calcCalmar(equities, maxDd);
        int[] ddStats = calcDrawdownStats(equities);
        BigDecimal underwater = equities.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(ddStats[1]).divide(BigDecimal.valueOf(equities.size()), 4, RoundingMode.HALF_UP);
        return new ReplayResult(points, maxDd, sharpe, var95, cvar95, calmar, ddStats[0], underwater);
    }

    /**
     * @return [最近回撤修复/水下天数, 水下交易日数]
     */
    private static int[] calcDrawdownStats(List<BigDecimal> equities) {
        BigDecimal peak = BigDecimal.ZERO;
        int underwaterDays = 0;
        int currentUnder = 0;
        int lastRecoveryOrUnder = 0;
        for (BigDecimal eq : equities) {
            if (eq.compareTo(peak) >= 0) {
                if (currentUnder > 0) {
                    lastRecoveryOrUnder = currentUnder;
                }
                peak = eq;
                currentUnder = 0;
            } else {
                underwaterDays++;
                currentUnder++;
                lastRecoveryOrUnder = currentUnder;
            }
        }
        return new int[]{lastRecoveryOrUnder, underwaterDays};
    }

    private static BigDecimal calcMaxDrawdown(List<BigDecimal> equities) {
        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal maxDd = BigDecimal.ZERO;
        for (BigDecimal eq : equities) {
            if (eq.compareTo(peak) > 0) {
                peak = eq;
            }
            if (peak.signum() > 0) {
                BigDecimal dd = peak.subtract(eq).divide(peak, MC);
                if (dd.compareTo(maxDd) > 0) {
                    maxDd = dd;
                }
            }
        }
        return maxDd.setScale(6, RoundingMode.HALF_UP);
    }

    private static BigDecimal calcSharpe(List<BigDecimal> equities) {
        if (equities.size() < 3) {
            return BigDecimal.ZERO;
        }
        List<BigDecimal> dailyReturns = new ArrayList<>();
        for (int i = 1; i < equities.size(); i++) {
            BigDecimal prev = equities.get(i - 1);
            BigDecimal curr = equities.get(i);
            if (prev.signum() > 0) {
                dailyReturns.add(curr.subtract(prev).divide(prev, MC));
            }
        }
        if (dailyReturns.size() < 2) {
            return BigDecimal.ZERO;
        }
        BigDecimal mean = dailyReturns.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(dailyReturns.size()), MC);
        BigDecimal variance = BigDecimal.ZERO;
        for (BigDecimal r : dailyReturns) {
            BigDecimal diff = r.subtract(mean);
            variance = variance.add(diff.multiply(diff, MC));
        }
        variance = variance.divide(BigDecimal.valueOf(dailyReturns.size() - 1), MC);
        if (variance.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        double std = Math.sqrt(variance.doubleValue());
        if (std == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(mean.doubleValue() / std * Math.sqrt(252)).setScale(6, RoundingMode.HALF_UP);
    }

    private static BigDecimal calcDailyVar95(List<BigDecimal> equities) {
        List<BigDecimal> rets = dailyRets(equities);
        if (rets.size() < 5) {
            return BigDecimal.ZERO;
        }
        rets.sort(BigDecimal::compareTo);
        int idx = Math.max(0, (int) Math.floor(rets.size() * 0.05) - 1);
        // VaR 用损失正数表示
        return rets.get(idx).negate().max(BigDecimal.ZERO).setScale(6, RoundingMode.HALF_UP);
    }

    private static BigDecimal calcDailyCvar95(List<BigDecimal> equities) {
        List<BigDecimal> rets = dailyRets(equities);
        if (rets.size() < 5) {
            return BigDecimal.ZERO;
        }
        rets.sort(BigDecimal::compareTo);
        int cut = Math.max(1, (int) Math.floor(rets.size() * 0.05));
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < cut; i++) {
            sum = sum.add(rets.get(i));
        }
        BigDecimal avg = sum.divide(BigDecimal.valueOf(cut), MC);
        return avg.negate().max(BigDecimal.ZERO).setScale(6, RoundingMode.HALF_UP);
    }

    private static List<BigDecimal> dailyRets(List<BigDecimal> equities) {
        List<BigDecimal> rets = new ArrayList<>();
        for (int i = 1; i < equities.size(); i++) {
            BigDecimal prev = equities.get(i - 1);
            BigDecimal curr = equities.get(i);
            if (prev.signum() > 0) {
                rets.add(curr.subtract(prev).divide(prev, MC));
            }
        }
        return rets;
    }

    private static BigDecimal calcCalmar(List<BigDecimal> equities, BigDecimal maxDrawdown) {
        if (equities.size() < 2 || Objects.isNull(maxDrawdown) || maxDrawdown.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal first = equities.get(0);
        BigDecimal last = equities.get(equities.size() - 1);
        if (first.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = last.subtract(first).divide(first, MC);
        double years = Math.max(equities.size() / 252.0, 1.0 / 252.0);
        double annual = Math.pow(1 + total.doubleValue(), 1.0 / years) - 1;
        return BigDecimal.valueOf(annual / maxDrawdown.doubleValue()).setScale(6, RoundingMode.HALF_UP);
    }
}
