package com.awe.apex.quant.backtest;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.BacktestCostResp;
import com.awe.apex.quant.domain.dto.RollingBacktestFoldResp;
import com.awe.apex.quant.domain.dto.RollingBacktestReq;
import com.awe.apex.quant.domain.dto.RollingBacktestResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.strategy.BarSeries;
import com.awe.apex.quant.strategy.Strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * 滚动样本外评估器
 */
public class RollingBacktestAnalyzer {

    private static final int MIN_TRAIN_DAYS = 60;
    private static final int MIN_TEST_DAYS = 20;
    private static final int MAX_FOLD_COUNT = 50;
    private static final int MAX_COST_SCALE = 8;
    private static final BigDecimal MAX_COST_RATE = new BigDecimal("0.05");

    private final BacktestEngine backtestEngine;

    /**
     * 创建滚动评估器
     *
     * @param backtestEngine 回测引擎
     */
    public RollingBacktestAnalyzer(BacktestEngine backtestEngine) {
        this.backtestEngine = backtestEngine;
    }

    /**
     * 执行滚动样本外评估
     *
     * @param code          证券代码
     * @param strategy      策略
     * @param stockBars     标的日线
     * @param benchmarkBars 基准日线
     * @param req           实验请求
     * @param costConfig    成本假设
     * @return 滚动评估结果
     */
    public RollingBacktestResp evaluate(String code, Strategy strategy, List<BarDaily> stockBars,
                                        List<BarDaily> benchmarkBars, RollingBacktestReq req,
                                        BacktestCostConfig costConfig) {
        return evaluate(code, strategy, stockBars, benchmarkBars, req, costConfig, false);
    }

    /**
     * 执行带证券涨跌停属性的滚动样本外评估
     *
     * @param code          证券代码
     * @param strategy      策略
     * @param stockBars     标的日线
     * @param benchmarkBars 基准日线
     * @param req           实验请求
     * @param costConfig    成本假设
     * @param stStock       是否主板 ST 股票
     * @return 滚动评估结果
     */
    public RollingBacktestResp evaluate(String code, Strategy strategy, List<BarDaily> stockBars,
                                        List<BarDaily> benchmarkBars, RollingBacktestReq req,
                                        BacktestCostConfig costConfig, boolean stStock) {
        if (CollUtil.isEmpty(stockBars)) {
            throw new BusinessException("标的日线为空，无法滚动评估");
        }
        if (CollUtil.isEmpty(benchmarkBars)) {
            throw new BusinessException("基准日线为空，无法滚动评估");
        }

        String windowMode = StringUtils.isNotBlank(req.getWindowMode())
                ? req.getWindowMode().toUpperCase() : "ROLLING";
        if (!"ROLLING".equals(windowMode) && !"EXPANDING".equals(windowMode)) {
            throw new BusinessException("窗口模式仅支持 ROLLING 或 EXPANDING");
        }
        int trainDays = Objects.nonNull(req.getTrainDays()) ? req.getTrainDays() : 252;
        int testDays = Objects.nonNull(req.getTestDays()) ? req.getTestDays() : 63;
        int stepDays = Objects.nonNull(req.getStepDays()) ? req.getStepDays() : testDays;
        if (trainDays < MIN_TRAIN_DAYS) {
            throw new BusinessException("样本内窗口不能少于60个交易日");
        }
        if (testDays < MIN_TEST_DAYS) {
            throw new BusinessException("样本外窗口不能少于20个交易日");
        }
        if (stepDays < testDays) {
            throw new BusinessException("步长不能小于样本外窗口，避免重复统计");
        }
        if (trainDays > 1250 || testDays > 500 || stepDays > 500) {
            throw new BusinessException("滚动窗口参数超过允许范围");
        }
        if (stockBars.size() < trainDays + testDays) {
            throw new BusinessException("日线不足，至少需要" + (trainDays + testDays) + "根");
        }

        BigDecimal initCash = Objects.nonNull(req.getInitCash())
                ? req.getInitCash() : new BigDecimal("1000000");
        if (initCash.signum() <= 0) {
            throw new BusinessException("初始资金必须大于0");
        }
        validateCost(costConfig);
        int possibleFoldCount = (stockBars.size() - trainDays - testDays) / stepDays + 1;
        if (possibleFoldCount > MAX_FOLD_COUNT) {
            throw new BusinessException("样本外窗口超过50个，请缩短日期范围或增大窗口");
        }
        List<RollingBacktestFoldResp> folds = new ArrayList<>();
        List<BigDecimal> outSampleDailyReturns = new ArrayList<>();
        int testStartIndex = trainDays;
        while (testStartIndex + testDays <= stockBars.size()) {
            int trainStartIndex = "EXPANDING".equals(windowMode) ? 0 : testStartIndex - trainDays;
            int testEndIndex = testStartIndex + testDays;

            List<BarDaily> trainBars = stockBars.subList(trainStartIndex, testStartIndex);
            List<BarDaily> evaluationBars = stockBars.subList(trainStartIndex, testEndIndex);
            BacktestResult inSampleResult = backtestEngine.run(
                    strategy, BarSeries.from(trainBars, stStock), initCash, costConfig, 0, true);
            BacktestResult outSampleResult = backtestEngine.run(
                    strategy, BarSeries.from(evaluationBars, stStock), initCash, costConfig,
                    testStartIndex - trainStartIndex, true);
            appendDailyReturns(outSampleDailyReturns, outSampleResult, initCash);

            LocalDate testBeginDate = stockBars.get(testStartIndex).getTradeDate();
            LocalDate testEndDate = stockBars.get(testEndIndex - 1).getTradeDate();
            BigDecimal benchmarkReturn = calculateBenchmarkReturn(
                    benchmarkBars, testBeginDate, testEndDate);
            BigDecimal inSampleReturn = defaultZero(inSampleResult.getTotalReturn());
            BigDecimal outSampleReturn = defaultZero(outSampleResult.getTotalReturn());
            BigDecimal inSampleAnnualReturn = defaultZero(inSampleResult.getAnnualReturn());
            BigDecimal outSampleAnnualReturn = defaultZero(outSampleResult.getAnnualReturn());
            folds.add(RollingBacktestFoldResp.builder()
                    .foldNo(folds.size() + 1)
                    .trainBeginDate(trainBars.get(0).getTradeDate())
                    .trainEndDate(trainBars.get(trainBars.size() - 1).getTradeDate())
                    .testBeginDate(testBeginDate)
                    .testEndDate(testEndDate)
                    .inSampleReturn(inSampleReturn)
                    .inSampleSharpe(inSampleResult.getSharpe())
                    .inSampleAnnualReturn(inSampleAnnualReturn)
                    .outSampleReturn(outSampleReturn)
                    .outSampleSharpe(outSampleResult.getSharpe())
                    .outSampleAnnualReturn(outSampleAnnualReturn)
                    .outSampleMaxDrawdown(outSampleResult.getMaxDrawdown())
                    .benchmarkReturn(benchmarkReturn)
                    .excessReturn(outSampleReturn.subtract(benchmarkReturn).setScale(6, RoundingMode.HALF_UP))
                    .annualReturnDecay(outSampleAnnualReturn.subtract(inSampleAnnualReturn)
                            .setScale(6, RoundingMode.HALF_UP))
                    .tradeCount(outSampleResult.getTradeCount())
                    .endingPositionQuantity(outSampleResult.getEndingPositionQuantity())
                    .endingPositionMarketValue(outSampleResult.getEndingPositionMarketValue())
                    .build());
            testStartIndex += stepDays;
        }

        if (folds.size() < 2) {
            throw new BusinessException("至少需要2个完整样本外窗口，请扩大日期范围或缩短窗口");
        }
        return aggregate(code, strategy.strategyId(), req.getBenchmarkCode(), windowMode,
                trainDays, testDays, stepDays, stockBars, benchmarkBars, costConfig, folds,
                strategy.strategyName(), outSampleDailyReturns, stStock, initCash);
    }

    private BigDecimal calculateBenchmarkReturn(List<BarDaily> benchmarkBars, LocalDate beginDate, LocalDate endDate) {
        BigDecimal beginPrice = null;
        BigDecimal endPrice = null;
        LocalDate firstDate = null;
        LocalDate lastDate = null;
        int validCount = 0;
        for (BarDaily benchmarkBar : benchmarkBars) {
            if (benchmarkBar.getTradeDate().isBefore(beginDate) || benchmarkBar.getTradeDate().isAfter(endDate)
                    || Objects.isNull(benchmarkBar.getClosePrice())) {
                continue;
            }
            if (Objects.isNull(firstDate)) {
                beginPrice = benchmarkBar.getOpenPrice();
                firstDate = benchmarkBar.getTradeDate();
            }
            endPrice = benchmarkBar.getClosePrice();
            lastDate = benchmarkBar.getTradeDate();
            validCount++;
        }
        if (validCount < 2 || !beginDate.equals(firstDate) || !endDate.equals(lastDate)
                || Objects.isNull(beginPrice) || Objects.isNull(endPrice) || beginPrice.signum() <= 0) {
            throw new BusinessException("基准日线未完整覆盖样本外窗口");
        }
        return endPrice.subtract(beginPrice).divide(beginPrice, 6, RoundingMode.HALF_UP);
    }

    private RollingBacktestResp aggregate(String code, String strategyId, String benchmarkCode,
                                          String windowMode, int trainDays, int testDays, int stepDays,
                                          List<BarDaily> stockBars, List<BarDaily> benchmarkBars,
                                          BacktestCostConfig costConfig,
                                          List<RollingBacktestFoldResp> folds, String strategyName,
                                          List<BigDecimal> outSampleDailyReturns, boolean stStock,
                                          BigDecimal initCash) {
        BigDecimal compoundedReturn = BigDecimal.ONE;
        BigDecimal compoundedBenchmark = BigDecimal.ONE;
        BigDecimal sharpeSum = BigDecimal.ZERO;
        BigDecimal decaySum = BigDecimal.ZERO;
        BigDecimal worstDrawdown = BigDecimal.ZERO;
        int positiveCount = 0;
        int benchmarkWinCount = 0;
        for (RollingBacktestFoldResp fold : folds) {
            compoundedReturn = compoundedReturn.multiply(BigDecimal.ONE.add(fold.getOutSampleReturn()));
            compoundedBenchmark = compoundedBenchmark.multiply(BigDecimal.ONE.add(fold.getBenchmarkReturn()));
            sharpeSum = sharpeSum.add(defaultZero(fold.getOutSampleSharpe()));
            decaySum = decaySum.add(defaultZero(fold.getAnnualReturnDecay()));
            if (fold.getOutSampleReturn().signum() > 0) {
                positiveCount++;
            }
            if (fold.getExcessReturn().signum() > 0) {
                benchmarkWinCount++;
            }
            if (fold.getOutSampleMaxDrawdown().compareTo(worstDrawdown) > 0) {
                worstDrawdown = fold.getOutSampleMaxDrawdown();
            }
        }

        BigDecimal foldCount = BigDecimal.valueOf(folds.size());
        BigDecimal evaluationDays = BigDecimal.valueOf(testDays + (long) (folds.size() - 1) * stepDays);
        BigDecimal selectedDays = BigDecimal.valueOf((long) folds.size() * testDays);
        BigDecimal outSampleReturn = compoundedReturn.subtract(BigDecimal.ONE).setScale(6, RoundingMode.HALF_UP);
        BigDecimal benchmarkReturn = compoundedBenchmark.subtract(BigDecimal.ONE).setScale(6, RoundingMode.HALF_UP);
        return RollingBacktestResp.builder()
                .code(code)
                .strategyId(strategyId)
                .strategyName(strategyName)
                .benchmarkCode(StringUtils.isNotBlank(benchmarkCode) ? benchmarkCode : "000300")
                .windowMode(windowMode)
                .dataBeginDate(stockBars.get(0).getTradeDate())
                .dataEndDate(stockBars.get(stockBars.size() - 1).getTradeDate())
                .outSampleBeginDate(folds.get(0).getTestBeginDate())
                .outSampleEndDate(folds.get(folds.size() - 1).getTestEndDate())
                .trainDays(trainDays)
                .testDays(testDays)
                .stepDays(stepDays)
                .initCash(initCash)
                .foldCount(folds.size())
                .coverageRate(selectedDays.divide(evaluationDays, 6, RoundingMode.HALF_UP))
                .compoundedOutSampleReturn(outSampleReturn)
                .compoundedBenchmarkReturn(benchmarkReturn)
                .compoundedExcessReturn(outSampleReturn.subtract(benchmarkReturn).setScale(6, RoundingMode.HALF_UP))
                .positiveFoldRate(BigDecimal.valueOf(positiveCount).divide(foldCount, 6, RoundingMode.HALF_UP))
                .benchmarkWinRate(BigDecimal.valueOf(benchmarkWinCount).divide(foldCount, 6, RoundingMode.HALF_UP))
                .outSampleSharpe(calculateSharpe(outSampleDailyReturns))
                .averageFoldSharpe(sharpeSum.divide(foldCount, 6, RoundingMode.HALF_UP))
                .worstOutSampleDrawdown(worstDrawdown.setScale(6, RoundingMode.HALF_UP))
                .averageAnnualReturnDecay(decaySum.divide(foldCount, 6, RoundingMode.HALF_UP))
                .executionModelVersion(BacktestEngine.EXECUTION_MODEL_VERSION)
                .priceAdjustment(BacktestEngine.PRICE_ADJUSTMENT)
                .dataFingerprint(calculateDataFingerprint(stockBars, benchmarkBars, stStock))
                .cost(BacktestCostResp.from(costConfig))
                .folds(folds)
                .disclaimer(BacktestEngine.DISCLAIMER)
                .build();
    }

    private String calculateDataFingerprint(List<BarDaily> stockBars, List<BarDaily> benchmarkBars,
                                            boolean stStock) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("STOCK_PRICE_LIMIT_RATES\n".getBytes(StandardCharsets.UTF_8));
            for (BigDecimal priceLimitRate : BarSeries.from(stockBars, stStock).getPriceLimitRates()) {
                digest.update((canonicalDecimal(priceLimitRate) + "\n").getBytes(StandardCharsets.UTF_8));
            }
            List<List<BarDaily>> dataSets = List.of(stockBars, benchmarkBars);
            String[] dataSetNames = new String[]{"STOCK", "BENCHMARK"};
            for (int dataSetIndex = 0; dataSetIndex < dataSets.size(); dataSetIndex++) {
                digest.update((dataSetNames[dataSetIndex] + "\n").getBytes(StandardCharsets.UTF_8));
                for (BarDaily bar : dataSets.get(dataSetIndex)) {
                    String canonicalBar = String.join("|",
                            Objects.toString(bar.getCode(), ""),
                            Objects.toString(bar.getTradeDate(), ""),
                            canonicalDecimal(bar.getOpenPrice()),
                            canonicalDecimal(bar.getHighPrice()),
                            canonicalDecimal(bar.getLowPrice()),
                            canonicalDecimal(bar.getClosePrice()),
                            canonicalDecimal(bar.getVolume())) + "\n";
                    digest.update(canonicalBar.getBytes(StandardCharsets.UTF_8));
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException("无法生成行情数据指纹", exception);
        }
    }

    private String canonicalDecimal(BigDecimal value) {
        return Objects.nonNull(value) ? value.stripTrailingZeros().toPlainString() : "";
    }

    private void appendDailyReturns(List<BigDecimal> dailyReturns, BacktestResult result, BigDecimal initCash) {
        BigDecimal previousEquity = initCash;
        for (BacktestEquityPoint equityPoint : result.getEquities()) {
            if (Objects.isNull(equityPoint.getEquity()) || previousEquity.signum() <= 0) {
                continue;
            }
            dailyReturns.add(equityPoint.getEquity().subtract(previousEquity)
                    .divide(previousEquity, 12, RoundingMode.HALF_UP));
            previousEquity = equityPoint.getEquity();
        }
    }

    private BigDecimal calculateSharpe(List<BigDecimal> dailyReturns) {
        if (dailyReturns.size() < 2) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        BigDecimal count = BigDecimal.valueOf(dailyReturns.size());
        BigDecimal mean = BigDecimal.ZERO;
        for (BigDecimal dailyReturn : dailyReturns) {
            mean = mean.add(dailyReturn);
        }
        mean = mean.divide(count, 12, RoundingMode.HALF_UP);
        BigDecimal variance = BigDecimal.ZERO;
        for (BigDecimal dailyReturn : dailyReturns) {
            BigDecimal difference = dailyReturn.subtract(mean);
            variance = variance.add(difference.multiply(difference));
        }
        variance = variance.divide(BigDecimal.valueOf(dailyReturns.size() - 1), 12, RoundingMode.HALF_UP);
        if (variance.signum() <= 0) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        double standardDeviation = Math.sqrt(variance.doubleValue());
        return BigDecimal.valueOf(mean.doubleValue() / standardDeviation * Math.sqrt(252))
                .setScale(6, RoundingMode.HALF_UP);
    }

    private void validateCost(BacktestCostConfig costConfig) {
        if (Objects.isNull(costConfig)) {
            throw new BusinessException("回测成本配置不能为空");
        }
        BigDecimal[] costRates = new BigDecimal[]{costConfig.getCommissionRate(), costConfig.getStampTaxRate(),
                costConfig.getBuySlippage(), costConfig.getSellSlippage()};
        for (BigDecimal costRate : costRates) {
            if (Objects.isNull(costRate) || costRate.signum() < 0 || costRate.compareTo(MAX_COST_RATE) > 0) {
                throw new BusinessException("成本和滑点必须在0到5%之间");
            }
            if (costRate.stripTrailingZeros().scale() > MAX_COST_SCALE) {
                throw new BusinessException("成本和滑点最多支持8位小数");
            }
        }
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return Objects.nonNull(value) ? value : BigDecimal.ZERO;
    }
}
