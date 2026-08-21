package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.domain.dto.AlphaComponentResp;
import com.awe.apex.quant.domain.dto.FactorCategoryResp;
import com.awe.apex.quant.domain.dto.FactorCenterResp;
import com.awe.apex.quant.domain.dto.FactorItemResp;
import com.awe.apex.quant.domain.dto.LimitUpLadderResp;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.NorthboundFlow;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.StockFinAbstract;
import com.awe.apex.quant.domain.entity.StockFinIndicator;
import com.awe.apex.quant.domain.entity.StockFundFlow;
import com.awe.apex.quant.factor.FactorCalculator;
import com.awe.apex.quant.indicator.IndicatorUtils;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.NorthboundFlowMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.StockFinAbstractMapper;
import com.awe.apex.quant.mapper.StockFinIndicatorMapper;
import com.awe.apex.quant.mapper.StockFundFlowMapper;
import com.awe.apex.quant.market.MarketCodeUtils;
import com.awe.apex.quant.service.IFactorCenterService;
import com.awe.apex.quant.service.ILimitUpLadderService;
import com.awe.apex.quant.service.IMarketBriefingService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 因子中心服务实现
 */
@Slf4j
@Service
public class FactorCenterServiceImpl implements IFactorCenterService {

    private static final String AVAILABLE = "AVAILABLE";
    private static final String MISSING = "MISSING";
    private static final long LIMIT_UP_CACHE_MILLIS = 60_000L;

    private volatile LimitUpLadderResp cachedLimitUpLadder;
    private volatile long limitUpCacheTime;

    @Resource
    private StockBasicMapper stockBasicMapper;

    @Resource
    private BarDailyMapper barDailyMapper;

    @Resource
    private StockFinAbstractMapper stockFinAbstractMapper;

    @Resource
    private StockFinIndicatorMapper stockFinIndicatorMapper;

    @Resource
    private StockFundFlowMapper stockFundFlowMapper;

    @Resource
    private NorthboundFlowMapper northboundFlowMapper;

    @Resource
    private IMarketBriefingService marketBriefingService;

    @Resource
    private ILimitUpLadderService limitUpLadderService;

    @Resource
    private FactorCalculator factorCalculator;

    /**
     * 查询个股七类因子与 Alpha 评分。
     *
     * @param code 证券代码
     * @return 因子中心详情
     */
    @Override
    public FactorCenterResp query(String code) {
        String securityCode = MarketCodeUtils.normalizeCode(code);
        StockBasic stockBasic = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                .eq(StockBasic::getCode, securityCode)
                .last("limit 1"));
        if (Objects.isNull(stockBasic)) {
            throw new BusinessException("未找到证券: " + securityCode);
        }

        // 1. 加载本地行情与财务数据
        List<BarDaily> dailyBars = new ArrayList<>(barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, securityCode)
                .orderByDesc(BarDaily::getTradeDate)
                .last("limit 140")));
        dailyBars.sort((leftBar, rightBar) -> leftBar.getTradeDate().compareTo(rightBar.getTradeDate()));
        List<StockFinAbstract> financialAbstracts = stockFinAbstractMapper.selectList(
                Wrappers.<StockFinAbstract>lambdaQuery()
                        .eq(StockFinAbstract::getCode, securityCode)
                        .orderByDesc(StockFinAbstract::getReportDate)
                        .last("limit 8"));
        List<StockFinIndicator> financialIndicators = stockFinIndicatorMapper.selectList(
                Wrappers.<StockFinIndicator>lambdaQuery()
                        .eq(StockFinIndicator::getCode, securityCode)
                        .orderByDesc(StockFinIndicator::getReportDate)
                        .last("limit 8"));
        StockFundFlow stockFundFlow = null;
        NorthboundFlow northboundFlow = null;
        try {
            stockFundFlow = stockFundFlowMapper.selectOne(Wrappers.<StockFundFlow>lambdaQuery()
                    .eq(StockFundFlow::getCode, securityCode)
                    .orderByDesc(StockFundFlow::getTradeDate)
                    .last("limit 1"));
            northboundFlow = northboundFlowMapper.selectOne(Wrappers.<NorthboundFlow>lambdaQuery()
                    .orderByDesc(NorthboundFlow::getTradeDate)
                    .last("limit 1"));
        } catch (Exception ex) {
            log.warn("因子中心读取资金流快照失败，证券代码={}，异常={}", securityCode, ex.getMessage());
        }

        // 2. 读取本地市场快照，不主动触发外网刷新
        MarketBriefingResp marketBriefing = null;
        try {
            marketBriefing = marketBriefingService.loadCachedBriefing();
        } catch (Exception ex) {
            log.warn("因子中心读取市场简报失败，证券代码={}，异常={}", securityCode, ex.getMessage());
        }
        LimitUpLadderResp limitUpLadder = loadLimitUpLadder(securityCode);

        StockFinAbstract latestAbstract = CollUtil.isNotEmpty(financialAbstracts)
                ? financialAbstracts.get(0) : null;
        StockFinIndicator latestIndicator = CollUtil.isNotEmpty(financialIndicators)
                ? financialIndicators.get(0) : null;
        LocalDate dailyAsOf = CollUtil.isNotEmpty(dailyBars)
                ? dailyBars.get(dailyBars.size() - 1).getTradeDate() : null;

        // 3. 构建七类因子
        List<FactorCategoryResp> categories = new ArrayList<>();
        categories.add(buildValuationCategory(stockBasic));
        categories.add(buildGrowthCategory(latestAbstract, latestIndicator, financialIndicators));
        categories.add(buildQualityCategory(latestAbstract, latestIndicator));
        categories.add(buildMomentumCategory(dailyBars, dailyAsOf));
        categories.add(buildTechnicalCategory(dailyBars, dailyAsOf));
        categories.add(buildCapitalCategory(dailyBars, stockFundFlow, northboundFlow, dailyAsOf));
        categories.add(buildMarketCategory(marketBriefing, limitUpLadder));

        // 4. 按固定权重计算 Alpha 分并返回覆盖率
        List<AlphaComponentResp> alphaComponents = buildAlphaComponents(
                dailyBars, latestAbstract, latestIndicator, marketBriefing, dailyAsOf);
        BigDecimal alphaScore = factorCalculator.calculateAlphaScore(alphaComponents);
        BigDecimal coverage = factorCalculator.calculateCoverage(alphaComponents);
        return FactorCenterResp.builder()
                .code(stockBasic.getCode())
                .name(stockBasic.getName())
                .market(stockBasic.getMarket())
                .industry(stockBasic.getIndustry())
                .latestPrice(stockBasic.getLatestPrice())
                .asOf(dailyAsOf)
                .alphaScore(alphaScore)
                .coverage(coverage)
                .alphaLabel(resolveAlphaLabel(alphaScore))
                .scoreModel("HEURISTIC_V1")
                .alphaComponents(alphaComponents)
                .categories(categories)
                .message(buildMessage(coverage, dailyAsOf))
                .build();
    }

    private FactorCategoryResp buildValuationCategory(StockBasic stockBasic) {
        LocalDate valuationAsOf = Objects.nonNull(stockBasic.getQuoteTime())
                ? stockBasic.getQuoteTime().toLocalDate() : null;
        List<FactorItemResp> factors = new ArrayList<>();
        factors.add(item("PE", "PE", stockBasic.getPeTtm(), null, "倍", "滚动市盈率 PE-TTM", valuationAsOf));
        factors.add(item("PB", "PB", stockBasic.getPb(), null, "倍", "市净率", valuationAsOf));
        factors.add(item("PS", "PS", null, null, "倍", "本地尚无可靠滚动营收市值比", valuationAsOf));
        factors.add(item("EV_EBITDA", "EV/EBITDA", null, null, "倍", "本地尚无企业价值与 EBITDA 标准字段", valuationAsOf));
        return category("VALUATION", "估值", "价格相对盈利、净资产与经营规模", factors);
    }

    private FactorCategoryResp buildGrowthCategory(StockFinAbstract latestAbstract,
                                                   StockFinIndicator latestIndicator,
                                                   List<StockFinIndicator> financialIndicators) {
        BigDecimal roeGrowth = calculateRoeGrowth(latestIndicator, financialIndicators);
        List<FactorItemResp> factors = new ArrayList<>();
        factors.add(item("REVENUE_GROWTH", "营收增长",
                Objects.nonNull(latestAbstract) ? latestAbstract.getRevenueYoy() : null,
                null, "%", "最新财报营业收入同比",
                Objects.nonNull(latestAbstract) ? latestAbstract.getReportDate() : null));
        factors.add(item("EARNINGS_GROWTH", "净利润增长",
                Objects.nonNull(latestAbstract) ? latestAbstract.getNetProfitYoy() : null,
                null, "%", "最新财报净利润同比",
                Objects.nonNull(latestAbstract) ? latestAbstract.getReportDate() : null));
        factors.add(item("ROE_GROWTH", "ROE增长", roeGrowth,
                null, "百分点", "最新 ROE 相对上年同期变化",
                Objects.nonNull(latestIndicator) ? latestIndicator.getReportDate() : null));
        return category("GROWTH", "成长", "收入、盈利和资本回报的增长质量", factors);
    }

    private FactorCategoryResp buildQualityCategory(StockFinAbstract latestAbstract,
                                                    StockFinIndicator latestIndicator) {
        LocalDate indicatorAsOf = Objects.nonNull(latestIndicator) ? latestIndicator.getReportDate() : null;
        LocalDate abstractAsOf = Objects.nonNull(latestAbstract) ? latestAbstract.getReportDate() : null;
        BigDecimal netMargin = Objects.nonNull(latestIndicator) && Objects.nonNull(latestIndicator.getNetMargin())
                ? latestIndicator.getNetMargin()
                : Objects.nonNull(latestAbstract) ? latestAbstract.getNetMargin() : null;
        BigDecimal debtRatio = Objects.nonNull(latestIndicator) && Objects.nonNull(latestIndicator.getDebtRatio())
                ? latestIndicator.getDebtRatio()
                : Objects.nonNull(latestAbstract) ? latestAbstract.getDebtRatio() : null;
        BigDecimal operatingCashFlowPerShare = Objects.nonNull(latestIndicator)
                && Objects.nonNull(latestIndicator.getOcfps())
                ? latestIndicator.getOcfps()
                : Objects.nonNull(latestAbstract) ? latestAbstract.getOcfps() : null;
        List<FactorItemResp> factors = new ArrayList<>();
        factors.add(item("ROA", "ROA", Objects.nonNull(latestIndicator) ? latestIndicator.getRoa() : null,
                null, "%", "总资产净利率，衡量全部资产创造净利润的效率", indicatorAsOf));
        factors.add(item("GROSS_MARGIN", "毛利率",
                Objects.nonNull(latestIndicator) ? latestIndicator.getGrossMargin() : null,
                null, "%", "销售毛利率，反映产品溢价和直接成本控制能力", indicatorAsOf));
        factors.add(item("NET_MARGIN", "净利率", netMargin,
                null, "%", "销售净利率，反映每单位收入最终转化为净利润的比例",
                Objects.nonNull(latestIndicator) && Objects.nonNull(latestIndicator.getNetMargin())
                        ? indicatorAsOf : abstractAsOf));
        factors.add(item("DEBT_RATIO", "资产负债率", debtRatio,
                null, "%", "负债总额占资产总额比例，需结合行业特征判断",
                Objects.nonNull(latestIndicator) && Objects.nonNull(latestIndicator.getDebtRatio())
                        ? indicatorAsOf : abstractAsOf));
        factors.add(item("OCFPS", "每股经营现金流", operatingCashFlowPerShare,
                null, "元", "经营活动现金流净额折算到每股，用于观察利润含金量",
                Objects.nonNull(latestIndicator) && Objects.nonNull(latestIndicator.getOcfps())
                        ? indicatorAsOf : abstractAsOf));
        return category("QUALITY", "质量", "盈利能力、财务稳健性与现金流质量", factors);
    }

    private FactorCategoryResp buildMomentumCategory(List<BarDaily> dailyBars, LocalDate asOf) {
        List<FactorItemResp> factors = new ArrayList<>();
        for (int period : List.of(5, 20, 60, 120)) {
            factors.add(item("MOMENTUM_" + period, period + "D",
                    factorCalculator.calculateReturn(dailyBars, period), null, "%",
                    period + " 个交易日收盘价收益", asOf));
        }
        return category("MOMENTUM", "动量", "不同交易周期的价格趋势强度", factors);
    }

    private FactorCategoryResp buildTechnicalCategory(List<BarDaily> dailyBars, LocalDate asOf) {
        List<BigDecimal> closePrices = new ArrayList<>();
        for (BarDaily dailyBar : dailyBars) {
            closePrices.add(dailyBar.getClosePrice());
        }
        int latestIndex = closePrices.size() - 1;
        BigDecimal rsi = latestIndex >= 14 ? IndicatorUtils.rsi(closePrices, 14, latestIndex) : null;
        List<FactorItemResp> factors = new ArrayList<>();
        factors.add(item("RSI", "RSI", rsi, null, null, "14 日相对强弱指标", asOf));
        factors.add(item("MACD", "MACD", factorCalculator.calculateMacdHist(dailyBars),
                null, null, "MACD 柱值，正值偏多、负值偏空", asOf));
        factors.add(item("ATR", "ATR", factorCalculator.calculateAtrPct(dailyBars),
                null, "%", "ATR14 占最新收盘价比例", asOf));
        factors.add(item("VOLATILITY", "波动率", factorCalculator.calculateAnnualizedVolatility(dailyBars, 20),
                null, "%", "近 20 日收益率年化波动率", asOf));
        return category("TECHNICAL", "技术", "趋势位置、波动和交易拥挤度", factors);
    }

    private FactorCategoryResp buildCapitalCategory(List<BarDaily> dailyBars,
                                                    StockFundFlow stockFundFlow,
                                                    NorthboundFlow northboundFlow,
                                                    LocalDate dailyAsOf) {
        BigDecimal latestAmount = CollUtil.isNotEmpty(dailyBars)
                ? dailyBars.get(dailyBars.size() - 1).getAmount() : null;
        BigDecimal amountYi = Objects.nonNull(latestAmount)
                ? latestAmount.divide(new BigDecimal("100000000"), 2, RoundingMode.HALF_UP) : null;
        BigDecimal mainNetInflowYi = Objects.nonNull(stockFundFlow) && Objects.nonNull(stockFundFlow.getMainNetInflow())
                ? stockFundFlow.getMainNetInflow().divide(new BigDecimal("100000000"), 2, RoundingMode.HALF_UP)
                : null;
        BigDecimal northboundNetBuyYi = Objects.nonNull(northboundFlow)
                && "PUBLISHED".equals(northboundFlow.getDataStatus())
                && Objects.nonNull(northboundFlow.getNetBuyAmount())
                ? northboundFlow.getNetBuyAmount().divide(new BigDecimal("100000000"), 2, RoundingMode.HALF_UP)
                : null;
        BigDecimal amountRatio = factorCalculator.calculateAmountRatio(dailyBars, 20);
        List<FactorItemResp> factors = new ArrayList<>();
        factors.add(item("MAIN_CAPITAL", "主力资金", mainNetInflowYi, null, "亿元",
                "个股最新交易日主力净流入", Objects.nonNull(stockFundFlow) ? stockFundFlow.getTradeDate() : null));
        factors.add(item("NORTHBOUND", "北向", northboundNetBuyYi, null, "亿元",
                "全市场北向资金最新披露净买额，不代表个股北向持仓变化",
                Objects.nonNull(northboundFlow) ? northboundFlow.getTradeDate() : null));
        factors.add(item("AMOUNT", "成交额", amountYi, null, "亿元", "最新交易日成交额", dailyAsOf));
        factors.add(item("AMOUNT_RATIO", "量能强度", amountRatio, null, "倍", "最新成交额相对前 20 日均额", dailyAsOf));
        return category("CAPITAL", "资金", "交易资金活跃度与增量资金", factors);
    }

    private FactorCategoryResp buildMarketCategory(MarketBriefingResp marketBriefing,
                                                   LimitUpLadderResp limitUpLadder) {
        LocalDate marketAsOf = Objects.nonNull(marketBriefing) ? marketBriefing.getAsOf()
                : Objects.nonNull(limitUpLadder) ? limitUpLadder.getTradeDate() : null;
        BigDecimal breadth = calculateBreadth(marketBriefing);
        String riseFallText = Objects.nonNull(marketBriefing)
                && (Objects.nonNull(marketBriefing.getBreadthUp()) || Objects.nonNull(marketBriefing.getBreadthDown()))
                ? valueText(marketBriefing.getBreadthUp()) + " / " + valueText(marketBriefing.getBreadthDown()) : null;
        String effectText = Objects.nonNull(marketBriefing) && Objects.nonNull(marketBriefing.getEffect())
                && Objects.nonNull(marketBriefing.getEffect().getMedianPctChg())
                ? marketBriefing.getEffect().getMedianPctChg().setScale(2, RoundingMode.HALF_UP) + "%" : null;
        List<FactorItemResp> factors = new ArrayList<>();
        factors.add(item("MARKET_BREADTH", "市场宽度", breadth, null, "%", "上涨家数占上涨与下跌家数之和", marketAsOf));
        factors.add(item("ADVANCE_DECLINE", "涨跌家数", null, riseFallText, null, "上涨家数 / 下跌家数", marketAsOf));
        factors.add(item("LIMIT_HEIGHT", "连板高度",
                Objects.nonNull(limitUpLadder) && Objects.nonNull(limitUpLadder.getTradeDate())
                        && Objects.nonNull(limitUpLadder.getMaxLianban())
                        ? BigDecimal.valueOf(limitUpLadder.getMaxLianban()) : null,
                null, "板", "最新涨停池最高连续涨停高度",
                Objects.nonNull(limitUpLadder) ? limitUpLadder.getTradeDate() : null));
        factors.add(item("SENTIMENT_CYCLE", "情绪周期", null,
                Objects.nonNull(marketBriefing) ? marketBriefing.getStance() : null,
                null, "市场简报立场：进攻、均衡或防守", marketAsOf));
        factors.add(item("MONEY_EFFECT", "赚钱效应", null, effectText,
                null, "全 A 涨幅中位数", marketAsOf));
        return category("MARKET", "市场", "全市场风险偏好与赚钱效应", factors);
    }

    private List<AlphaComponentResp> buildAlphaComponents(List<BarDaily> dailyBars,
                                                         StockFinAbstract latestAbstract,
                                                         StockFinIndicator latestIndicator,
                                                         MarketBriefingResp marketBriefing,
                                                         LocalDate dailyAsOf) {
        List<BigDecimal> momentumValues = new ArrayList<>();
        momentumValues.add(factorCalculator.calculateReturn(dailyBars, 20));
        momentumValues.add(factorCalculator.calculateReturn(dailyBars, 60));
        momentumValues.add(factorCalculator.calculateReturn(dailyBars, 120));
        BigDecimal momentum = averageAvailable(momentumValues);
        BigDecimal roe = Objects.nonNull(latestIndicator) ? latestIndicator.getRoe()
                : Objects.nonNull(latestAbstract) ? latestAbstract.getRoe() : null;
        BigDecimal earningsGrowth = Objects.nonNull(latestAbstract) ? latestAbstract.getNetProfitYoy() : null;
        BigDecimal amountRatio = factorCalculator.calculateAmountRatio(dailyBars, 20);
        BigDecimal marketStrength = Objects.nonNull(marketBriefing)
                && Boolean.TRUE.equals(marketBriefing.getDataSufficient())
                && Objects.nonNull(marketBriefing.getAsOf())
                && Objects.nonNull(marketBriefing.getStanceScore())
                ? BigDecimal.valueOf(marketBriefing.getStanceScore()) : null;

        List<AlphaComponentResp> components = new ArrayList<>();
        components.add(component("MOMENTUM", "Momentum", 30, momentum,
                factorCalculator.scoreMomentum(momentum), dailyAsOf,
                "20D、60D、120D 收益均值，标准分=min(max(50+1.5×均值,0),100)"));
        LocalDate roeAsOf = Objects.nonNull(latestIndicator) ? latestIndicator.getReportDate()
                : Objects.nonNull(latestAbstract) ? latestAbstract.getReportDate() : null;
        components.add(component("ROE", "ROE", 20, roe,
                factorCalculator.scoreRoe(roe), roeAsOf,
                "最新报告期 ROE，标准分=min(max((ROE+5)/30×100,0),100)"));
        components.add(component("EARNINGS_GROWTH", "Earnings Growth", 20, earningsGrowth,
                factorCalculator.scoreGrowth(earningsGrowth),
                Objects.nonNull(latestAbstract) ? latestAbstract.getReportDate() : null,
                "净利润同比，标准分=min(max(50+同比,0),100)"));
        components.add(component("VOLUME", "Volume", 15, amountRatio,
                factorCalculator.scoreVolume(amountRatio), dailyAsOf,
                "成交额相对前20日均额，标准分=min(量能倍数×50,100)"));
        components.add(component("MARKET_STRENGTH", "Market Strength", 15, marketStrength,
                factorCalculator.scoreMarket(marketStrength),
                Objects.nonNull(marketBriefing) ? marketBriefing.getAsOf() : null,
                "仅在市场简报数据充分时采用风险偏好评分"));
        return components;
    }

    private BigDecimal calculateRoeGrowth(StockFinIndicator latestIndicator,
                                          List<StockFinIndicator> financialIndicators) {
        if (Objects.isNull(latestIndicator) || Objects.isNull(latestIndicator.getReportDate())
                || Objects.isNull(latestIndicator.getRoe()) || CollUtil.isEmpty(financialIndicators)) {
            return null;
        }
        LocalDate comparableDate = latestIndicator.getReportDate().minusYears(1);
        for (StockFinIndicator financialIndicator : financialIndicators) {
            if (comparableDate.equals(financialIndicator.getReportDate()) && Objects.nonNull(financialIndicator.getRoe())) {
                return latestIndicator.getRoe().subtract(financialIndicator.getRoe())
                        .setScale(2, RoundingMode.HALF_UP);
            }
        }
        return null;
    }

    private BigDecimal calculateBreadth(MarketBriefingResp marketBriefing) {
        if (Objects.isNull(marketBriefing) || Objects.isNull(marketBriefing.getBreadthUp())
                || Objects.isNull(marketBriefing.getBreadthDown())) {
            return null;
        }
        int directionalCount = marketBriefing.getBreadthUp() + marketBriefing.getBreadthDown();
        if (directionalCount <= 0) {
            return null;
        }
        return BigDecimal.valueOf(marketBriefing.getBreadthUp())
                .divide(BigDecimal.valueOf(directionalCount), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal averageAvailable(List<BigDecimal> values) {
        BigDecimal valueSum = BigDecimal.ZERO;
        int valueCount = 0;
        for (BigDecimal value : values) {
            if (Objects.nonNull(value)) {
                valueSum = valueSum.add(value);
                valueCount++;
            }
        }
        return valueCount == 0 ? null
                : valueSum.divide(BigDecimal.valueOf(valueCount), 2, RoundingMode.HALF_UP);
    }

    private FactorCategoryResp category(String key, String label, String description,
                                        List<FactorItemResp> factors) {
        return FactorCategoryResp.builder()
                .key(key)
                .label(label)
                .description(description)
                .factors(factors)
                .build();
    }

    private FactorItemResp item(String key, String label, BigDecimal value, String displayValue,
                                String unit, String description, LocalDate asOf) {
        boolean available = Objects.nonNull(value) || (Objects.nonNull(displayValue) && !displayValue.isBlank());
        return FactorItemResp.builder()
                .key(key)
                .label(label)
                .value(value)
                .displayValue(displayValue)
                .unit(unit)
                .status(available ? AVAILABLE : MISSING)
                .description(description)
                .asOf(asOf)
                .build();
    }

    private AlphaComponentResp component(String key, String label, int weight, BigDecimal rawValue,
                                         BigDecimal score, LocalDate asOf, String description) {
        return AlphaComponentResp.builder()
                .key(key)
                .label(label)
                .weight(BigDecimal.valueOf(weight))
                .rawValue(rawValue)
                .score(score)
                .available(Objects.nonNull(score))
                .asOf(asOf)
                .description(description)
                .build();
    }

    private String resolveAlphaLabel(BigDecimal alphaScore) {
        if (Objects.isNull(alphaScore)) {
            return "数据不足";
        }
        if (alphaScore.compareTo(new BigDecimal("75")) >= 0) {
            return "强势";
        }
        if (alphaScore.compareTo(new BigDecimal("60")) >= 0) {
            return "偏强";
        }
        if (alphaScore.compareTo(new BigDecimal("40")) >= 0) {
            return "中性";
        }
        return "偏弱";
    }

    private String buildMessage(BigDecimal coverage, LocalDate dailyAsOf) {
        String cutoff = Objects.nonNull(dailyAsOf) ? "日线截至 " + dailyAsOf : "本地暂无日线";
        if (Objects.isNull(coverage) || coverage.compareTo(new BigDecimal("100")) < 0) {
            return cutoff + "；HEURISTIC_V1 规则综合分按可用权重归一化，覆盖率 " + coverage + "%";
        }
        return cutoff + "；HEURISTIC_V1 规则综合分五项权重数据完整";
    }

    private LimitUpLadderResp loadLimitUpLadder(String securityCode) {
        long currentTime = System.currentTimeMillis();
        LimitUpLadderResp currentCache = cachedLimitUpLadder;
        if (Objects.nonNull(currentCache) && currentTime - limitUpCacheTime < LIMIT_UP_CACHE_MILLIS) {
            return currentCache;
        }
        try {
            LimitUpLadderResp latestLadder = limitUpLadderService.ladder(null);
            cachedLimitUpLadder = latestLadder;
            limitUpCacheTime = currentTime;
            return latestLadder;
        } catch (Exception ex) {
            log.warn("因子中心读取连板天梯失败，证券代码={}，异常={}", securityCode, ex.getMessage());
            return currentCache;
        }
    }

    private String valueText(Integer value) {
        return Objects.nonNull(value) ? String.valueOf(value) : "-";
    }
}
