package com.awe.apex.quant.screener;

import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.bo.ScreenerCandidateBO;
import com.awe.apex.quant.domain.bo.ScreenerRuleEvaluationBO;
import com.awe.apex.quant.domain.dto.IntradayAcceptanceMetric;
import com.awe.apex.quant.domain.dto.ScreenerMarketSnapshot;
import com.awe.apex.quant.domain.dto.ScreenerStrategyRuleResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.LimitUpPool;
import com.awe.apex.quant.domain.enums.ScreenerOperatorEnum;
import com.awe.apex.quant.domain.enums.ScreenerRuleTypeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * 选股策略规则评估器
 */
@Component
public class ScreenerStrategyRuleEvaluator {

    private static final EnumSet<ScreenerRuleTypeEnum> HISTORICAL_RULES = EnumSet.of(
            ScreenerRuleTypeEnum.RANGE_RETURN,
            ScreenerRuleTypeEnum.LIMIT_UP_COUNT,
            ScreenerRuleTypeEnum.UP_DAYS,
            ScreenerRuleTypeEnum.RS20,
            ScreenerRuleTypeEnum.ATR_PCT,
            ScreenerRuleTypeEnum.PRICE_POSITION
    );

    private static final EnumSet<ScreenerRuleTypeEnum> INTRADAY_RULES = EnumSet.of(
            ScreenerRuleTypeEnum.INTRADAY_ABOVE_AVG_RATIO,
            ScreenerRuleTypeEnum.INTRADAY_CURRENT_ABOVE_AVG,
            ScreenerRuleTypeEnum.INTRADAY_MAX_BELOW_MINUTES
    );

    private static final EnumSet<ScreenerRuleTypeEnum> LIMIT_POOL_RULES = EnumSet.of(
            ScreenerRuleTypeEnum.LIMIT_UP_LEVEL,
            ScreenerRuleTypeEnum.FIRST_SEAL_TIME,
            ScreenerRuleTypeEnum.LAST_SEAL_TIME,
            ScreenerRuleTypeEnum.BREAK_COUNT,
            ScreenerRuleTypeEnum.SEAL_AMOUNT,
            ScreenerRuleTypeEnum.THEME_LINKAGE_COUNT
    );

    @Resource
    private ScreenerMetricCalculator metricCalculator;

    /**
     * 判断规则是否属于历史日线阶段。
     *
     * @param rule 规则
     * @return 是否历史规则
     */
    public boolean isHistoricalRule(ScreenerStrategyRuleResp rule) {
        return HISTORICAL_RULES.contains(type(rule));
    }

    /**
     * 判断规则是否属于分时复核阶段。
     *
     * @param rule 规则
     * @return 是否分时规则
     */
    public boolean isIntradayRule(ScreenerStrategyRuleResp rule) {
        return INTRADAY_RULES.contains(type(rule));
    }

    /**
     * 判断策略是否依赖涨停池。
     *
     * @param rule 规则
     * @return 是否涨停池规则
     */
    public boolean isLimitPoolRule(ScreenerStrategyRuleResp rule) {
        return LIMIT_POOL_RULES.contains(type(rule));
    }

    /**
     * 评估实时截面或涨停池规则。
     *
     * @param candidate 候选股票
     * @param rule      规则
     * @return 评估结果
     */
    public ScreenerRuleEvaluationBO evaluateSnapshot(ScreenerCandidateBO candidate,
                                                      ScreenerStrategyRuleResp rule) {
        ScreenerRuleTypeEnum ruleType = type(rule);
        ScreenerMarketSnapshot snapshot = candidate.getSnapshot();
        LimitUpPool limitUp = candidate.getLimitUpPool();
        return switch (ruleType) {
            case MARKET_BOARD -> evaluateText(resolveBoard(snapshot.getCode()), rule);
            case EXCLUDE_ST -> evaluateExcludeSt(snapshot.getName(), rule);
            case PE_TTM -> evaluateDecimal(snapshot.getPeTtm(), rule);
            case PB -> evaluateDecimal(snapshot.getPb(), rule);
            case TOTAL_MV -> evaluateDecimal(snapshot.getTotalMv(), rule);
            case CIRC_MV -> evaluateDecimal(snapshot.getCircMv(), rule);
            case PCT_CHG -> evaluateDecimal(snapshot.getPctChg(), rule);
            case TURNOVER_RATE -> evaluateDecimal(snapshot.getTurnoverRate(), rule);
            case VOLUME_RATIO -> evaluateDecimal(snapshot.getVolumeRatio(), rule);
            case AMOUNT -> evaluateDecimal(Objects.nonNull(snapshot.getAmount()) ? snapshot.getAmount()
                    : Objects.nonNull(limitUp) ? limitUp.getAmount() : null, rule);
            case LIMIT_UP_LEVEL -> Objects.isNull(limitUp)
                    ? failed("未进入当日涨停池") : evaluateInteger(limitUp.getLianban(), rule);
            case FIRST_SEAL_TIME -> Objects.isNull(limitUp)
                    ? failed("未进入当日涨停池") : evaluateText(limitUp.getFirstSealTime(), rule);
            case LAST_SEAL_TIME -> Objects.isNull(limitUp)
                    ? failed("未进入当日涨停池") : evaluateText(limitUp.getLastSealTime(), rule);
            case BREAK_COUNT -> Objects.isNull(limitUp)
                    ? failed("未进入当日涨停池") : evaluateInteger(limitUp.getBreakCount(), rule);
            case SEAL_AMOUNT -> Objects.isNull(limitUp)
                    ? failed("未进入当日涨停池") : evaluateDecimal(limitUp.getSealAmount(), rule);
            case THEME_LINKAGE_COUNT -> Objects.isNull(limitUp)
                    ? failed("未进入当日涨停池") : evaluateInteger(candidate.getThemeLinkageCount(), rule);
            default -> failed("规则不属于实时截面阶段");
        };
    }

    /**
     * 评估历史日线规则。
     *
     * @param candidate       候选股票
     * @param rule            规则
     * @param benchmarkRet20  沪深300近20日涨跌幅
     * @return 评估结果
     */
    public ScreenerRuleEvaluationBO evaluateHistorical(ScreenerCandidateBO candidate,
                                                        ScreenerStrategyRuleResp rule,
                                                        BigDecimal benchmarkRet20) {
        ScreenerRuleTypeEnum ruleType = type(rule);
        List<BarDaily> bars = candidate.getBars();
        int lookback = Objects.nonNull(rule.getLookbackDays()) ? rule.getLookbackDays() : 20;
        return switch (ruleType) {
            case RANGE_RETURN -> {
                BigDecimal value = metricCalculator.calculateReturnPct(bars, lookback);
                candidate.setRangeReturn(value);
                yield evaluateDecimal(value, rule);
            }
            case LIMIT_UP_COUNT -> {
                if (Objects.isNull(bars) || bars.size() < lookback) {
                    yield missing("近 " + lookback + " 个交易日日线不足");
                }
                int value = metricCalculator.countLimitUps(bars, lookback);
                candidate.setLimitUpCount(value);
                yield evaluateInteger(value, rule);
            }
            case UP_DAYS -> {
                if (Objects.isNull(bars) || bars.size() < 2) {
                    yield missing("连续上涨计算所需日线不足");
                }
                int value = metricCalculator.calculateUpDays(bars);
                candidate.setUpDays(value);
                yield evaluateInteger(value, rule);
            }
            case RS20 -> {
                BigDecimal stockRet20 = metricCalculator.calculateReturnPct(bars, 20);
                if (Objects.isNull(stockRet20) || Objects.isNull(benchmarkRet20)) {
                    yield missing("股票或沪深300近20日日线不足");
                }
                BigDecimal value = stockRet20.subtract(benchmarkRet20).setScale(2, RoundingMode.HALF_UP);
                candidate.setRs20(value);
                yield evaluateDecimal(value, rule);
            }
            case ATR_PCT -> {
                BigDecimal value = metricCalculator.calculateAtrPct(bars, 14);
                candidate.setAtrPct(value);
                yield evaluateDecimal(value, rule);
            }
            case PRICE_POSITION -> {
                if (Objects.isNull(bars) || bars.size() < lookback) {
                    yield missing("近 " + lookback + " 个交易日日线不足");
                }
                BigDecimal value = metricCalculator.calculatePricePositionPct(bars, lookback);
                candidate.setPricePosition(value);
                yield evaluateDecimal(value, rule);
            }
            default -> failed("规则不属于历史日线阶段");
        };
    }

    /**
     * 评估分时均价承接规则。
     *
     * @param candidate 候选股票
     * @param rule      规则
     * @return 评估结果
     */
    public ScreenerRuleEvaluationBO evaluateIntraday(ScreenerCandidateBO candidate,
                                                      ScreenerStrategyRuleResp rule) {
        IntradayAcceptanceMetric metric = candidate.getIntradayMetric();
        if (Objects.isNull(metric) || Objects.isNull(metric.getPointCount()) || metric.getPointCount() == 0) {
            return missing("09:35 后无有效分时点");
        }
        return switch (type(rule)) {
            case INTRADAY_ABOVE_AVG_RATIO -> evaluateDecimal(metric.getAboveAvgRatio(), rule);
            case INTRADAY_CURRENT_ABOVE_AVG -> evaluateBoolean(metric.getCurrentAboveAvg(), rule);
            case INTRADAY_MAX_BELOW_MINUTES -> evaluateInteger(metric.getMaxConsecutiveBelowMinutes(), rule);
            default -> failed("规则不属于分时复核阶段");
        };
    }

    private ScreenerRuleEvaluationBO evaluateDecimal(BigDecimal actual, ScreenerStrategyRuleResp rule) {
        if (Objects.isNull(actual)) {
            return missing(rule.getRuleName() + "数据缺失");
        }
        ScreenerOperatorEnum operator = operator(rule);
        boolean passed;
        if (ScreenerOperatorEnum.BETWEEN.equals(operator)) {
            passed = Objects.nonNull(rule.getMinValue()) && Objects.nonNull(rule.getMaxValue())
                    && actual.compareTo(rule.getMinValue()) >= 0 && actual.compareTo(rule.getMaxValue()) <= 0;
        } else {
            BigDecimal expected = Objects.nonNull(rule.getMinValue()) ? rule.getMinValue()
                    : Objects.nonNull(rule.getIntValue()) ? BigDecimal.valueOf(rule.getIntValue()) : null;
            if (Objects.isNull(expected)) {
                return missing("规则比较值缺失");
            }
            passed = compare(actual.compareTo(expected), operator);
        }
        return result(passed, decimal(actual), expected(rule));
    }

    private ScreenerRuleEvaluationBO evaluateInteger(Integer actual, ScreenerStrategyRuleResp rule) {
        if (Objects.isNull(actual)) {
            return missing(rule.getRuleName() + "数据缺失");
        }
        Integer expected = rule.getIntValue();
        if (Objects.isNull(expected) && Objects.nonNull(rule.getMinValue())) {
            expected = rule.getMinValue().intValue();
        }
        if (Objects.isNull(expected)) {
            return missing("规则比较值缺失");
        }
        return result(compare(actual.compareTo(expected), operator(rule)), String.valueOf(actual), expected(rule));
    }

    private ScreenerRuleEvaluationBO evaluateText(String actual, ScreenerStrategyRuleResp rule) {
        if (StringUtils.isBlank(actual)) {
            return missing(rule.getRuleName() + "数据缺失");
        }
        String expected = rule.getTextValue();
        if (StringUtils.isBlank(expected)) {
            return missing("规则比较值缺失");
        }
        boolean passed = compare(actual.compareToIgnoreCase(expected), operator(rule));
        return result(passed, actual, expected(rule));
    }

    private ScreenerRuleEvaluationBO evaluateBoolean(Boolean actual, ScreenerStrategyRuleResp rule) {
        if (Objects.isNull(actual)) {
            return missing(rule.getRuleName() + "数据缺失");
        }
        if (Objects.isNull(rule.getBoolValue())) {
            return missing("规则比较值缺失");
        }
        return result(actual.equals(rule.getBoolValue()), actual ? "是" : "否", expected(rule));
    }

    private ScreenerRuleEvaluationBO evaluateExcludeSt(String name, ScreenerStrategyRuleResp rule) {
        boolean st = StringUtils.isNotBlank(name) && name.toUpperCase().contains("ST");
        boolean exclude = !Boolean.FALSE.equals(rule.getBoolValue());
        return result(!exclude || !st, st ? "ST" : "非ST", expected(rule));
    }

    private boolean compare(int comparison, ScreenerOperatorEnum operator) {
        return switch (operator) {
            case EQ -> comparison == 0;
            case GT -> comparison > 0;
            case GTE -> comparison >= 0;
            case LT -> comparison < 0;
            case LTE -> comparison <= 0;
            case BETWEEN -> false;
        };
    }

    private String expected(ScreenerStrategyRuleResp rule) {
        if (StringUtils.isNotBlank(rule.getSummary())) {
            return rule.getSummary();
        }
        ScreenerOperatorEnum operator = operator(rule);
        if (ScreenerOperatorEnum.BETWEEN.equals(operator)) {
            return decimal(rule.getMinValue()) + " - " + decimal(rule.getMaxValue());
        }
        Object value = Objects.nonNull(rule.getMinValue()) ? decimal(rule.getMinValue())
                : Objects.nonNull(rule.getIntValue()) ? rule.getIntValue()
                : StringUtils.isNotBlank(rule.getTextValue()) ? rule.getTextValue()
                : Objects.nonNull(rule.getBoolValue()) ? (rule.getBoolValue() ? "是" : "否") : "-";
        return operator.getDesc() + " " + value;
    }

    private String resolveBoard(String code) {
        if (StringUtils.isBlank(code)) {
            return "UNKNOWN";
        }
        if (code.startsWith("688") || code.startsWith("689")) {
            return "STAR";
        }
        if (code.startsWith("300") || code.startsWith("301")) {
            return "CHINEXT";
        }
        if (code.startsWith("8") || code.startsWith("4") || code.startsWith("9")) {
            return "BSE";
        }
        return "MAIN_BOARD";
    }

    private ScreenerRuleTypeEnum type(ScreenerStrategyRuleResp rule) {
        return ScreenerRuleTypeEnum.valueOf(rule.getRuleType());
    }

    private ScreenerOperatorEnum operator(ScreenerStrategyRuleResp rule) {
        return ScreenerOperatorEnum.valueOf(rule.getOperatorCode());
    }

    private ScreenerRuleEvaluationBO result(boolean passed, String actual, String expected) {
        return ScreenerRuleEvaluationBO.builder()
                .passed(passed)
                .missing(false)
                .actualValue(actual)
                .expectedValue(expected)
                .build();
    }

    private ScreenerRuleEvaluationBO missing(String message) {
        return ScreenerRuleEvaluationBO.builder()
                .passed(false)
                .missing(true)
                .actualValue("缺失")
                .missingMessage(message)
                .build();
    }

    private ScreenerRuleEvaluationBO failed(String actual) {
        return ScreenerRuleEvaluationBO.builder()
                .passed(false)
                .missing(false)
                .actualValue(actual)
                .build();
    }

    private String decimal(BigDecimal value) {
        return Objects.nonNull(value) ? value.stripTrailingZeros().toPlainString() : "-";
    }
}
