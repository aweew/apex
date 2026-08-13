package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.JsonUtils;
import com.awe.apex.quant.domain.dto.TraderPortfolioResp;
import com.awe.apex.quant.domain.dto.TraderPositionResp;
import com.awe.apex.quant.domain.dto.TraderRankingResp;
import com.awe.apex.quant.domain.dto.SmartMoneyFactorStatsResp;
import com.awe.apex.quant.domain.entity.SmartMoneyFactor;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.Trade;
import com.awe.apex.quant.domain.entity.Trader;
import com.awe.apex.quant.domain.entity.TraderPortfolioDaily;
import com.awe.apex.quant.domain.entity.TraderPosition;
import com.awe.apex.quant.domain.entity.TraderProfile;
import com.awe.apex.quant.domain.entity.TraderRankingDaily;
import com.awe.apex.quant.mapper.SmartMoneyFactorMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.TradeMapper;
import com.awe.apex.quant.mapper.TraderMapper;
import com.awe.apex.quant.mapper.TraderPortfolioDailyMapper;
import com.awe.apex.quant.mapper.TraderPositionMapper;
import com.awe.apex.quant.mapper.TraderProfileMapper;
import com.awe.apex.quant.mapper.TraderRankingDailyMapper;
import com.awe.apex.quant.service.ISmartTraderAnalyticsService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Smart Trader 投影与统计计算服务实现。 */
@Service
public class SmartTraderAnalyticsServiceImpl implements ISmartTraderAnalyticsService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal DEFAULT_INITIAL_CAPITAL = new BigDecimal("1000000.00");

    @Resource private TraderMapper traderMapper;
    @Resource private TradeMapper tradeMapper;
    @Resource private TraderPositionMapper traderPositionMapper;
    @Resource private TraderPortfolioDailyMapper traderPortfolioDailyMapper;
    @Resource private TraderRankingDailyMapper traderRankingDailyMapper;
    @Resource private TraderProfileMapper traderProfileMapper;
    @Resource private SmartMoneyFactorMapper smartMoneyFactorMapper;
    @Resource private StockBasicMapper stockBasicMapper;
    @Resource private BarDailyMapper barDailyMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rebuild(LocalDate tradeDate) {
        LocalDate snapshotDate = Objects.nonNull(tradeDate) ? tradeDate : LocalDate.now();
        List<Trader> traders = traderMapper.selectList(Wrappers.<Trader>lambdaQuery().eq(Trader::getStatus, "ACTIVE"));
        for (Trader trader : traders) rebuildTrader(trader, snapshotDate);
        rebuildRankings(snapshotDate);
        rebuildFactors(snapshotDate);
    }

    /** {@inheritDoc} */
    @Override
    public List<TraderPositionResp> positions(Long traderId) {
        requireTrader(traderId);
        List<TraderPosition> positions = traderPositionMapper.selectList(Wrappers.<TraderPosition>lambdaQuery()
                .eq(TraderPosition::getTraderId, traderId).gt(TraderPosition::getQuantity, 0)
                .orderByDesc(TraderPosition::getMarketValue));
        List<TraderPositionResp> result = new ArrayList<>();
        for (TraderPosition position : positions) result.add(toPositionResp(position));
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public TraderPortfolioResp portfolio(Long traderId) {
        requireTrader(traderId);
        TraderPortfolioDaily daily = traderPortfolioDailyMapper.selectOne(Wrappers.<TraderPortfolioDaily>lambdaQuery()
                .eq(TraderPortfolioDaily::getTraderId, traderId).orderByDesc(TraderPortfolioDaily::getTradeDate).last("LIMIT 1"));
        if (Objects.isNull(daily)) throw new BusinessException("交易者尚无账户快照");
        return TraderPortfolioResp.builder().traderId(traderId).tradeDate(daily.getTradeDate()).cash(daily.getCash())
                .marketValue(daily.getMarketValue()).totalAsset(daily.getTotalAsset()).dailyProfitRate(daily.getDailyProfitRate())
                .totalProfitRate(daily.getTotalProfitRate()).maxDrawdown(daily.getMaxDrawdown()).positions(positions(traderId)).build();
    }

    /** {@inheritDoc} */
    @Override
    public List<TraderRankingResp> ranking(String type) {
        String rankingType = Objects.isNull(type) ? "TOTAL" : type.trim().toUpperCase();
        LocalDate latestDate = latestRankingDate();
        if (Objects.isNull(latestDate)) return new ArrayList<>();
        List<TraderRankingDaily> records = traderRankingDailyMapper.selectList(Wrappers.<TraderRankingDaily>lambdaQuery()
                .eq(TraderRankingDaily::getTradeDate, latestDate));
        records.sort("DAILY".equals(rankingType) ? Comparator.comparing(TraderRankingDaily::getDailyRanking)
                : "STEADY".equals(rankingType) ? Comparator.comparing(TraderRankingDaily::getSteadyRanking)
                : Comparator.comparing(TraderRankingDaily::getReturnRanking));
        List<TraderRankingResp> result = new ArrayList<>();
        for (TraderRankingDaily record : records) {
            Trader trader = traderMapper.selectById(record.getTraderId());
            int rank = "DAILY".equals(rankingType) ? record.getDailyRanking() : "STEADY".equals(rankingType) ? record.getSteadyRanking() : record.getReturnRanking();
            result.add(TraderRankingResp.builder().ranking(rank).traderId(record.getTraderId()).traderName(Objects.isNull(trader) ? null : trader.getName())
                    .totalReturn(record.getTotalReturn()).dailyReturn(record.getDailyReturn()).maxDrawdown(record.getMaxDrawdown()).traderScore(record.getTraderScore()).build());
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public TraderProfile profile(Long traderId) {
        requireTrader(traderId);
        TraderProfile profile = traderProfileMapper.selectOne(Wrappers.<TraderProfile>lambdaQuery().eq(TraderProfile::getTraderId, traderId).last("LIMIT 1"));
        if (Objects.isNull(profile)) throw new BusinessException("交易者画像尚未生成");
        return profile;
    }

    /** {@inheritDoc} */
    @Override
    public List<SmartMoneyFactor> factors(LocalDate tradeDate) {
        LocalDate date = Objects.nonNull(tradeDate) ? tradeDate : latestRankingDate();
        if (Objects.isNull(date)) return new ArrayList<>();
        return smartMoneyFactorMapper.selectList(Wrappers.<SmartMoneyFactor>lambdaQuery()
                .eq(SmartMoneyFactor::getTradeDate, date).orderByDesc(SmartMoneyFactor::getFactorValue));
    }

    /** {@inheritDoc} */
    @Override
    public List<SmartMoneyFactorStatsResp> factorStats() {
        List<SmartMoneyFactor> factors = smartMoneyFactorMapper.selectList(Wrappers.<SmartMoneyFactor>lambdaQuery().orderByDesc(SmartMoneyFactor::getTradeDate));
        List<SmartMoneyFactorStatsResp> result = new ArrayList<>();
        result.add(buildFactorStats(factors, 1));
        result.add(buildFactorStats(factors, 5));
        result.add(buildFactorStats(factors, 10));
        result.add(buildFactorStats(factors, 20));
        return result;
    }

    private void rebuildTrader(Trader trader, LocalDate snapshotDate) {
        List<Trade> trades = tradeMapper.selectList(Wrappers.<Trade>lambdaQuery().eq(Trade::getTraderId, trader.getId())
                .eq(Trade::getStatus, "VALID").le(Trade::getTradeTime, snapshotDate.plusDays(1).atStartOfDay()).orderByAsc(Trade::getTradeTime).orderByAsc(Trade::getId));
        Map<String, TraderPosition> positions = new HashMap<>();
        BigDecimal cash = Objects.nonNull(trader.getInitialCapital()) ? trader.getInitialCapital() : DEFAULT_INITIAL_CAPITAL;
        for (Trade trade : trades) cash = applyTrade(positions, cash, trade);
        traderPositionMapper.delete(Wrappers.<TraderPosition>lambdaQuery().eq(TraderPosition::getTraderId, trader.getId()));
        BigDecimal marketValue = ZERO;
        for (TraderPosition position : positions.values()) {
            if (position.getQuantity() <= 0) continue;
            enrichQuote(position, snapshotDate);
            marketValue = marketValue.add(position.getMarketValue());
            traderPositionMapper.insert(position);
        }
        writePortfolioDaily(trader, snapshotDate, cash, marketValue);
        writeProfile(trader.getId(), positions, snapshotDate);
    }

    private BigDecimal applyTrade(Map<String, TraderPosition> positions, BigDecimal cash, Trade trade) {
        TraderPosition position = positions.get(trade.getSymbol());
        if (Objects.isNull(position)) {
            position = TraderPosition.builder().traderId(trade.getTraderId()).symbol(trade.getSymbol()).stockName(trade.getStockName())
                    .quantity(0).avgCost(ZERO).build();
            positions.put(trade.getSymbol(), position);
        }
        BigDecimal amount = trade.getAmount();
        if ("BUY".equals(trade.getSide())) {
            int newQuantity = position.getQuantity() + trade.getQuantity();
            BigDecimal cost = position.getAvgCost().multiply(BigDecimal.valueOf(position.getQuantity())).add(amount);
            position.setQuantity(newQuantity); position.setAvgCost(cost.divide(BigDecimal.valueOf(newQuantity), 4, RoundingMode.HALF_UP));
            return cash.subtract(amount);
        }
        if (trade.getQuantity() > position.getQuantity()) {
            throw new BusinessException("交易者缺少足够历史持仓，不能生成账户快照");
        }
        int sellQuantity = trade.getQuantity();
        position.setQuantity(position.getQuantity() - sellQuantity);
        if (position.getQuantity() == 0) position.setAvgCost(ZERO);
        return cash.add(trade.getPrice().multiply(BigDecimal.valueOf(sellQuantity)).setScale(2, RoundingMode.HALF_UP));
    }

    private void enrichQuote(TraderPosition position, LocalDate snapshotDate) {
        StockBasic stock = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery().eq(StockBasic::getCode, position.getSymbol()).last("LIMIT 1"));
        BigDecimal marketPrice = null;
        if (snapshotDate.equals(LocalDate.now()) && Objects.nonNull(stock) && Objects.nonNull(stock.getLatestPrice()) && stock.getLatestPrice().signum() > 0) {
            marketPrice = stock.getLatestPrice();
        }
        if (Objects.isNull(marketPrice)) {
            List<BarDaily> bars = barDailyMapper.selectRecentBars(position.getSymbol(), snapshotDate, 1);
            if (!bars.isEmpty() && Objects.nonNull(bars.get(0).getClosePrice()) && bars.get(0).getClosePrice().signum() > 0) {
                marketPrice = bars.get(0).getClosePrice();
            }
        }
        if (Objects.isNull(marketPrice)) marketPrice = position.getAvgCost();
        BigDecimal marketValue = marketPrice.multiply(BigDecimal.valueOf(position.getQuantity())).setScale(2, RoundingMode.HALF_UP);
        BigDecimal costValue = position.getAvgCost().multiply(BigDecimal.valueOf(position.getQuantity()));
        position.setMarketPrice(marketPrice); position.setMarketValue(marketValue); position.setProfit(marketValue.subtract(costValue).setScale(2, RoundingMode.HALF_UP));
        position.setProfitRate(costValue.signum() == 0 ? ZERO : marketValue.subtract(costValue).divide(costValue, 6, RoundingMode.HALF_UP)); position.setUpdateTime(LocalDateTime.now());
    }

    private void writePortfolioDaily(Trader trader, LocalDate snapshotDate, BigDecimal cash, BigDecimal marketValue) {
        BigDecimal initialCapital = Objects.nonNull(trader.getInitialCapital()) ? trader.getInitialCapital() : DEFAULT_INITIAL_CAPITAL;
        TraderPortfolioDaily yesterday = traderPortfolioDailyMapper.selectOne(Wrappers.<TraderPortfolioDaily>lambdaQuery().eq(TraderPortfolioDaily::getTraderId, trader.getId()).lt(TraderPortfolioDaily::getTradeDate, snapshotDate).orderByDesc(TraderPortfolioDaily::getTradeDate).last("LIMIT 1"));
        BigDecimal totalAsset = cash.add(marketValue).setScale(2, RoundingMode.HALF_UP);
        BigDecimal yesterdayAsset = Objects.isNull(yesterday) ? initialCapital : yesterday.getTotalAsset();
        BigDecimal dailyProfit = totalAsset.subtract(yesterdayAsset); BigDecimal totalProfit = totalAsset.subtract(initialCapital);
        BigDecimal dailyRate = yesterdayAsset.signum() == 0 ? ZERO : dailyProfit.divide(yesterdayAsset, 6, RoundingMode.HALF_UP);
        BigDecimal totalRate = initialCapital.signum() == 0 ? ZERO : totalProfit.divide(initialCapital, 6, RoundingMode.HALF_UP);
        List<TraderPortfolioDaily> history = traderPortfolioDailyMapper.selectList(Wrappers.<TraderPortfolioDaily>lambdaQuery()
                .eq(TraderPortfolioDaily::getTraderId, trader.getId()).lt(TraderPortfolioDaily::getTradeDate, snapshotDate));
        BigDecimal peak = initialCapital;
        for (TraderPortfolioDaily item : history) {
            peak = peak.max(item.getTotalAsset());
        }
        peak = peak.max(totalAsset);
        BigDecimal drawdown = peak.signum() == 0 ? ZERO : peak.subtract(totalAsset).divide(peak, 6, RoundingMode.HALF_UP);
        TraderPortfolioDaily daily = traderPortfolioDailyMapper.selectOne(Wrappers.<TraderPortfolioDaily>lambdaQuery().eq(TraderPortfolioDaily::getTraderId, trader.getId()).eq(TraderPortfolioDaily::getTradeDate, snapshotDate).last("LIMIT 1"));
        if (Objects.isNull(daily)) daily = TraderPortfolioDaily.builder().traderId(trader.getId()).tradeDate(snapshotDate).createTime(LocalDateTime.now()).deleted(0).build();
        daily.setCash(cash); daily.setMarketValue(marketValue); daily.setTotalAsset(totalAsset); daily.setDailyProfit(dailyProfit); daily.setDailyProfitRate(dailyRate); daily.setTotalProfit(totalProfit); daily.setTotalProfitRate(totalRate); daily.setMaxDrawdown(drawdown); daily.setUpdateTime(LocalDateTime.now());
        if (Objects.isNull(daily.getId())) traderPortfolioDailyMapper.insert(daily); else traderPortfolioDailyMapper.updateById(daily);
    }

    private void rebuildRankings(LocalDate snapshotDate) {
        List<TraderPortfolioDaily> portfolios = new ArrayList<>(traderPortfolioDailyMapper.selectList(
                Wrappers.<TraderPortfolioDaily>lambdaQuery().eq(TraderPortfolioDaily::getTradeDate, snapshotDate)));
        portfolios.sort(Comparator.comparing(TraderPortfolioDaily::getTotalProfitRate).reversed());
        for (int index = 0; index < portfolios.size(); index++) writeRanking(portfolios.get(index), snapshotDate, index + 1, "TOTAL");
        portfolios.sort(Comparator.comparing(TraderPortfolioDaily::getDailyProfitRate).reversed());
        for (int index = 0; index < portfolios.size(); index++) writeRanking(portfolios.get(index), snapshotDate, index + 1, "DAILY");
        portfolios.sort(Comparator.comparing(this::steadyScore).reversed());
        for (int index = 0; index < portfolios.size(); index++) writeRanking(portfolios.get(index), snapshotDate, index + 1, "STEADY");
    }

    private void writeRanking(TraderPortfolioDaily portfolio, LocalDate snapshotDate, int rank, String type) {
        TraderRankingDaily ranking = traderRankingDailyMapper.selectOne(Wrappers.<TraderRankingDaily>lambdaQuery().eq(TraderRankingDaily::getTradeDate, snapshotDate).eq(TraderRankingDaily::getTraderId, portfolio.getTraderId()).last("LIMIT 1"));
        if (Objects.isNull(ranking)) ranking = TraderRankingDaily.builder().tradeDate(snapshotDate).traderId(portfolio.getTraderId()).createTime(LocalDateTime.now()).deleted(0).build();
        int totalTrades = Math.toIntExact(tradeMapper.selectCount(Wrappers.<Trade>lambdaQuery().eq(Trade::getTraderId, portfolio.getTraderId()).eq(Trade::getStatus, "VALID")));
        BigDecimal winRate = totalTrades == 0 ? ZERO : portfolio.getTotalProfitRate().signum() > 0 ? BigDecimal.ONE : ZERO;
        BigDecimal profitLossRatio = portfolio.getMaxDrawdown().signum() == 0 ? portfolio.getTotalProfitRate().abs() : portfolio.getTotalProfitRate().abs().divide(portfolio.getMaxDrawdown(), 6, RoundingMode.HALF_UP);
        BigDecimal score = portfolio.getTotalProfitRate().multiply(new BigDecimal("0.40")).add(BigDecimal.ONE.subtract(portfolio.getMaxDrawdown()).multiply(new BigDecimal("0.20"))).add(winRate.multiply(new BigDecimal("0.15"))).add(profitLossRatio.min(BigDecimal.ONE).multiply(new BigDecimal("0.15"))).add(BigDecimal.ONE.subtract(portfolio.getMaxDrawdown()).multiply(new BigDecimal("0.10")));
        ranking.setTotalReturn(portfolio.getTotalProfitRate()); ranking.setDailyReturn(portfolio.getDailyProfitRate()); ranking.setMaxDrawdown(portfolio.getMaxDrawdown()); ranking.setWinRate(winRate); ranking.setProfitLossRatio(profitLossRatio); ranking.setSharpe(null); ranking.setTraderScore(score.setScale(6, RoundingMode.HALF_UP));
        if ("TOTAL".equals(type)) ranking.setReturnRanking(rank); else if ("DAILY".equals(type)) ranking.setDailyRanking(rank); else ranking.setSteadyRanking(rank);
        ranking.setUpdateTime(LocalDateTime.now()); if (Objects.isNull(ranking.getId())) traderRankingDailyMapper.insert(ranking); else traderRankingDailyMapper.updateById(ranking);
    }

    private BigDecimal steadyScore(TraderPortfolioDaily portfolio) { return portfolio.getTotalProfitRate().subtract(portfolio.getMaxDrawdown()); }

    private void rebuildFactors(LocalDate snapshotDate) {
        List<TraderRankingDaily> rankings = traderRankingDailyMapper.selectList(Wrappers.<TraderRankingDaily>lambdaQuery().eq(TraderRankingDaily::getTradeDate, snapshotDate).le(TraderRankingDaily::getReturnRanking, 20));
        Map<String, BigDecimal> netBuyAmounts = new HashMap<>(); Map<String, Integer> traderCounts = new HashMap<>(); BigDecimal totalAsset = ZERO;
        for (TraderRankingDaily ranking : rankings) {
            TraderPortfolioDaily portfolio = traderPortfolioDailyMapper.selectOne(Wrappers.<TraderPortfolioDaily>lambdaQuery().eq(TraderPortfolioDaily::getTraderId, ranking.getTraderId()).eq(TraderPortfolioDaily::getTradeDate, snapshotDate).last("LIMIT 1"));
            if (Objects.nonNull(portfolio)) totalAsset = totalAsset.add(portfolio.getTotalAsset());
            List<Trade> trades = tradeMapper.selectList(Wrappers.<Trade>lambdaQuery().eq(Trade::getTraderId, ranking.getTraderId()).eq(Trade::getStatus, "VALID").ge(Trade::getTradeTime, snapshotDate.atStartOfDay()).lt(Trade::getTradeTime, snapshotDate.plusDays(1).atStartOfDay()));
            for (Trade trade : trades) { BigDecimal signed = "BUY".equals(trade.getSide()) ? trade.getAmount() : trade.getAmount().negate(); netBuyAmounts.merge(trade.getSymbol(), signed, BigDecimal::add); traderCounts.merge(trade.getSymbol(), 1, Integer::sum); }
        }
        smartMoneyFactorMapper.delete(Wrappers.<SmartMoneyFactor>lambdaQuery().eq(SmartMoneyFactor::getTradeDate, snapshotDate));
        for (Map.Entry<String, BigDecimal> entry : netBuyAmounts.entrySet()) {
            StockBasic stock = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery().eq(StockBasic::getCode, entry.getKey()).last("LIMIT 1"));
            BigDecimal factor = totalAsset.signum() == 0 ? ZERO : entry.getValue().divide(totalAsset, 6, RoundingMode.HALF_UP);
            int count = traderCounts.get(entry.getKey());
            List<BarDaily> bars = barDailyMapper.selectOutcomeBars(entry.getKey(), snapshotDate, 20);
            SmartMoneyFactor smartMoneyFactor = SmartMoneyFactor.builder().tradeDate(snapshotDate).symbol(entry.getKey()).stockName(Objects.isNull(stock) ? null : stock.getName()).netBuyAmount(entry.getValue()).traderTotalAsset(totalAsset).factorValue(factor).traderCount(count).consensus(BigDecimal.valueOf(count).divide(BigDecimal.valueOf(Math.max(1, rankings.size())), 6, RoundingMode.HALF_UP)).return1d(factorReturn(bars, 1)).return5d(factorReturn(bars, 5)).return10d(factorReturn(bars, 10)).return20d(factorReturn(bars, 20)).createTime(LocalDateTime.now()).updateTime(LocalDateTime.now()).build();
            smartMoneyFactorMapper.insert(smartMoneyFactor);
        }
    }

    private void writeProfile(Long traderId, Map<String, TraderPosition> positions, LocalDate snapshotDate) {
        TraderPortfolioDaily portfolio = traderPortfolioDailyMapper.selectOne(Wrappers.<TraderPortfolioDaily>lambdaQuery().eq(TraderPortfolioDaily::getTraderId, traderId).eq(TraderPortfolioDaily::getTradeDate, snapshotDate).last("LIMIT 1"));
        if (Objects.isNull(portfolio)) return;
        BigDecimal marketValue = portfolio.getMarketValue(); BigDecimal largestWeight = ZERO;
        List<String> industries = new ArrayList<>();
        for (TraderPosition position : positions.values()) if (position.getQuantity() > 0 && marketValue.signum() > 0) largestWeight = largestWeight.max(position.getMarketValue().divide(marketValue, 6, RoundingMode.HALF_UP));
        for (TraderPosition position : positions.values()) {
            StockBasic stock = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery().eq(StockBasic::getCode, position.getSymbol()).last("LIMIT 1"));
            if (Objects.nonNull(stock) && com.awe.apex.common.util.StringUtils.isNotBlank(stock.getIndustry()) && !industries.contains(stock.getIndustry())) industries.add(stock.getIndustry());
        }
        String style = largestWeight.compareTo(new BigDecimal("0.50")) >= 0 ? "集中型" : portfolio.getMaxDrawdown().compareTo(new BigDecimal("0.15")) <= 0 ? "稳健型" : "趋势型";
        TraderProfile profile = traderProfileMapper.selectOne(Wrappers.<TraderProfile>lambdaQuery().eq(TraderProfile::getTraderId, traderId).last("LIMIT 1"));
        if (Objects.isNull(profile)) profile = TraderProfile.builder().traderId(traderId).createTime(LocalDateTime.now()).deleted(0).build();
        profile.setStyle(style); profile.setPreferredIndustries(JsonUtils.toJsonString(industries)); profile.setAverageHoldingDays(null); profile.setWinRate(portfolio.getTotalProfitRate().signum() > 0 ? BigDecimal.ONE : ZERO); profile.setProfitLossRatio(portfolio.getMaxDrawdown().signum() == 0 ? ZERO : portfolio.getTotalProfitRate().abs().divide(portfolio.getMaxDrawdown(), 6, RoundingMode.HALF_UP)); profile.setMaxDrawdown(portfolio.getMaxDrawdown()); profile.setTurnoverRate(null); profile.setVolatility(null); profile.setConcentration(largestWeight); profile.setSummary("基于已确认交易与当前行情生成；样本不足时画像仅供观察。"); profile.setUpdateTime(LocalDateTime.now());
        if (Objects.isNull(profile.getId())) traderProfileMapper.insert(profile); else traderProfileMapper.updateById(profile);
    }

    private BigDecimal factorReturn(List<BarDaily> bars, int holdingDays) {
        if (bars.size() < holdingDays || Objects.isNull(bars.get(0).getOpenPrice()) || bars.get(0).getOpenPrice().signum() <= 0
                || Objects.isNull(bars.get(holdingDays - 1).getClosePrice())) {
            return null;
        }
        return bars.get(holdingDays - 1).getClosePrice().subtract(bars.get(0).getOpenPrice())
                .divide(bars.get(0).getOpenPrice(), 6, RoundingMode.HALF_UP);
    }

    private SmartMoneyFactorStatsResp buildFactorStats(List<SmartMoneyFactor> factors, int holdingDays) {
        List<SmartMoneyFactor> samples = new ArrayList<>();
        for (SmartMoneyFactor factor : factors) {
            BigDecimal returned = factorReturn(factor, holdingDays);
            if (Objects.nonNull(returned)) samples.add(factor);
        }
        if (samples.isEmpty()) return SmartMoneyFactorStatsResp.builder().holdingDays(holdingDays).sampleCount(0).build();
        BigDecimal totalReturn = ZERO; int winCount = 0;
        for (SmartMoneyFactor sample : samples) {
            BigDecimal returned = factorReturn(sample, holdingDays);
            totalReturn = totalReturn.add(returned);
            if (returned.signum() > 0) winCount++;
        }
        BigDecimal meanReturn = totalReturn.divide(BigDecimal.valueOf(samples.size()), 6, RoundingMode.HALF_UP);
        return SmartMoneyFactorStatsResp.builder().holdingDays(holdingDays).sampleCount(samples.size()).averageReturn(meanReturn)
                .winRate(BigDecimal.valueOf(winCount).divide(BigDecimal.valueOf(samples.size()), 6, RoundingMode.HALF_UP))
                .informationCoefficient(correlation(samples, holdingDays)).build();
    }

    private BigDecimal factorReturn(SmartMoneyFactor factor, int holdingDays) {
        return holdingDays == 1 ? factor.getReturn1d() : holdingDays == 5 ? factor.getReturn5d()
                : holdingDays == 10 ? factor.getReturn10d() : factor.getReturn20d();
    }

    private BigDecimal correlation(List<SmartMoneyFactor> samples, int holdingDays) {
        if (samples.size() < 2) return null;
        BigDecimal factorTotal = ZERO; BigDecimal returnTotal = ZERO;
        for (SmartMoneyFactor sample : samples) { factorTotal = factorTotal.add(sample.getFactorValue()); returnTotal = returnTotal.add(factorReturn(sample, holdingDays)); }
        BigDecimal factorMean = factorTotal.divide(BigDecimal.valueOf(samples.size()), 12, RoundingMode.HALF_UP);
        BigDecimal returnMean = returnTotal.divide(BigDecimal.valueOf(samples.size()), 12, RoundingMode.HALF_UP);
        BigDecimal covariance = ZERO; BigDecimal factorVariance = ZERO; BigDecimal returnVariance = ZERO;
        for (SmartMoneyFactor sample : samples) {
            BigDecimal factorDiff = sample.getFactorValue().subtract(factorMean); BigDecimal returnDiff = factorReturn(sample, holdingDays).subtract(returnMean);
            covariance = covariance.add(factorDiff.multiply(returnDiff)); factorVariance = factorVariance.add(factorDiff.multiply(factorDiff)); returnVariance = returnVariance.add(returnDiff.multiply(returnDiff));
        }
        if (factorVariance.signum() == 0 || returnVariance.signum() == 0) return null;
        BigDecimal denominator = factorVariance.multiply(returnVariance).sqrt(new MathContext(12, RoundingMode.HALF_UP));
        return denominator.signum() == 0 ? null : covariance.divide(denominator, 6, RoundingMode.HALF_UP);
    }

    private Trader requireTrader(Long traderId) { Trader trader = traderMapper.selectById(traderId); if (Objects.isNull(trader)) throw new BusinessException("交易者不存在"); return trader; }
    private TraderPositionResp toPositionResp(TraderPosition position) { return TraderPositionResp.builder().symbol(position.getSymbol()).stockName(position.getStockName()).quantity(position.getQuantity()).avgCost(position.getAvgCost()).marketPrice(position.getMarketPrice()).marketValue(position.getMarketValue()).profit(position.getProfit()).profitRate(position.getProfitRate()).build(); }
    private LocalDate latestRankingDate() { TraderRankingDaily daily = traderRankingDailyMapper.selectOne(Wrappers.<TraderRankingDaily>lambdaQuery().orderByDesc(TraderRankingDaily::getTradeDate).last("LIMIT 1")); return Objects.isNull(daily) ? null : daily.getTradeDate(); }
}
