package com.awe.apex.quant.screener;

import com.awe.apex.quant.domain.dto.ScreenerStrategyResp;
import com.awe.apex.quant.domain.dto.ScreenerStrategyRuleResp;
import com.awe.apex.quant.domain.enums.ScreenerOperatorEnum;
import com.awe.apex.quant.domain.enums.ScreenerRuleTypeEnum;
import com.awe.apex.quant.domain.enums.ScreenerRunModeEnum;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统选股模板只读注册表
 */
@Component
public class ScreenerStrategyTemplateRegistry {

    private static final String DISCLAIMER = "常用公开筛选思路，收益未经验证，不构成投资建议。";

    private final Map<String, ScreenerStrategyResp> templateMap = new LinkedHashMap<>();

    /**
     * 初始化系统模板。
     */
    public ScreenerStrategyTemplateRegistry() {
        registerMainBoardAcceptance();
        registerBreakoutVolume();
        registerRelativeStrengthTrend();
        registerLowPositionVolume();
        registerMidCapValuation();
        registerPublicFirstBoard();
        registerSectorResonanceFirstBoard();
        registerLowPositionFirstBoard();
        registerLimitUpComeback();
        registerVolumeContractionPullback();
    }

    /**
     * 查询全部系统模板。
     *
     * @return 只读模板列表
     */
    public List<ScreenerStrategyResp> listTemplates() {
        return new ArrayList<>(templateMap.values());
    }

    /**
     * 按稳定标识查询系统模板。
     *
     * @param templateKey 模板标识
     * @return 模板，不存在时为空
     */
    public ScreenerStrategyResp getTemplate(String templateKey) {
        return templateMap.get(templateKey);
    }

    private void registerMainBoardAcceptance() {
        List<ScreenerStrategyRuleResp> rules = new ArrayList<>();
        rules.add(textRule(ScreenerRuleTypeEnum.MARKET_BOARD, "MAIN_BOARD", "沪深主板", 10));
        rules.add(booleanRule(ScreenerRuleTypeEnum.EXCLUDE_ST, true, "排除 ST", 20));
        rules.add(rangeRule(ScreenerRuleTypeEnum.TURNOVER_RATE, "5", "10", "换手率 5% - 10%", 30));
        rules.add(integerRule(ScreenerRuleTypeEnum.LIMIT_UP_COUNT, ScreenerOperatorEnum.GTE,
                1, 20, "近 20 个交易日至少 1 次涨停", 40));
        rules.add(rangeRule(ScreenerRuleTypeEnum.PCT_CHG, "3", "5", "当日涨幅 3% - 5%", 50));
        rules.add(numberRule(ScreenerRuleTypeEnum.VOLUME_RATIO, ScreenerOperatorEnum.GT,
                "1", "实时量比大于 1", 60));
        rules.add(rangeRule(ScreenerRuleTypeEnum.TOTAL_MV, "5000000000", "20000000000",
                "总市值 50亿 - 200亿", 70));
        rules.add(numberRule(ScreenerRuleTypeEnum.INTRADAY_ABOVE_AVG_RATIO, ScreenerOperatorEnum.GTE,
                "95", "09:35 后均价线上方占比至少 95%", 80));
        rules.add(booleanRule(ScreenerRuleTypeEnum.INTRADAY_CURRENT_ABOVE_AVG, true,
                "当前价不低于分时均价", 90));
        rules.add(integerRule(ScreenerRuleTypeEnum.INTRADAY_MAX_BELOW_MINUTES, ScreenerOperatorEnum.LTE,
                3, null, "连续跌破均价不超过 3 分钟", 100));
        register("MAIN_BOARD_STRONG_ACCEPTANCE", "主板强势承接",
                "主板强势股的实时换手、量比、涨幅、近期涨停和分时均价承接联合筛选。", rules);
    }

    private void registerBreakoutVolume() {
        List<ScreenerStrategyRuleResp> rules = new ArrayList<>();
        rules.add(booleanRule(ScreenerRuleTypeEnum.EXCLUDE_ST, true, "排除 ST", 10));
        rules.add(rangeRule(ScreenerRuleTypeEnum.PCT_CHG, "1.5", "7", "当日涨幅 1.5% - 7%", 20));
        rules.add(numberRule(ScreenerRuleTypeEnum.VOLUME_RATIO, ScreenerOperatorEnum.GTE,
                "1.5", "实时量比至少 1.5", 30));
        rules.add(numberRule(ScreenerRuleTypeEnum.RANGE_RETURN, ScreenerOperatorEnum.GTE,
                "5", "近 20 个交易日涨幅至少 5%", 40, 20));
        rules.add(rangeRule(ScreenerRuleTypeEnum.TOTAL_MV, "3000000000", "50000000000",
                "总市值 30亿 - 500亿", 50));
        register("BREAKOUT_VOLUME", "放量突破",
                "用实时量比和阶段涨幅确认价格突破，适合继续人工检查突破位置。", rules);
    }

    private void registerRelativeStrengthTrend() {
        List<ScreenerStrategyRuleResp> rules = new ArrayList<>();
        rules.add(booleanRule(ScreenerRuleTypeEnum.EXCLUDE_ST, true, "排除 ST", 10));
        rules.add(numberRule(ScreenerRuleTypeEnum.RS20, ScreenerOperatorEnum.GTE,
                "3", "20 日相对沪深300强度至少 3%", 20));
        rules.add(integerRule(ScreenerRuleTypeEnum.UP_DAYS, ScreenerOperatorEnum.GTE,
                2, null, "连续上涨至少 2 天", 30));
        rules.add(rangeRule(ScreenerRuleTypeEnum.ATR_PCT, "1", "5", "ATR14 占现价 1% - 5%", 40));
        register("RELATIVE_STRENGTH_TREND", "相对强势趋势",
                "在相对沪深300走强的股票中筛选趋势连续、波动不过度的候选。", rules);
    }

    private void registerLowPositionVolume() {
        List<ScreenerStrategyRuleResp> rules = new ArrayList<>();
        rules.add(booleanRule(ScreenerRuleTypeEnum.EXCLUDE_ST, true, "排除 ST", 10));
        rules.add(numberRule(ScreenerRuleTypeEnum.PRICE_POSITION, ScreenerOperatorEnum.LTE,
                "30", "最新价位于近 120 日价格区间下方 30%", 20, 120));
        rules.add(numberRule(ScreenerRuleTypeEnum.VOLUME_RATIO, ScreenerOperatorEnum.GTE,
                "1.5", "实时量比至少 1.5", 30));
        rules.add(rangeRule(ScreenerRuleTypeEnum.RANGE_RETURN, "0", "8",
                "近 5 个交易日涨幅 0% - 8%", 40, 5));
        register("LOW_POSITION_VOLUME", "低位放量",
                "从阶段低位中筛选温和反弹并出现放量的候选，避免追逐已大幅拉升标的。", rules);
    }

    private void registerMidCapValuation() {
        List<ScreenerStrategyRuleResp> rules = new ArrayList<>();
        rules.add(booleanRule(ScreenerRuleTypeEnum.EXCLUDE_ST, true, "排除 ST", 10));
        rules.add(rangeRule(ScreenerRuleTypeEnum.PE_TTM, "5", "35", "滚动市盈率 5 - 35", 20));
        rules.add(rangeRule(ScreenerRuleTypeEnum.PB, "0.5", "4", "市净率 0.5 - 4", 30));
        rules.add(rangeRule(ScreenerRuleTypeEnum.TOTAL_MV, "5000000000", "30000000000",
                "总市值 50亿 - 300亿", 40));
        register("MID_CAP_VALUATION", "中盘估值筛选",
                "按市盈率、市净率和中等市值范围过滤候选，不代表低估或买入结论。", rules);
    }

    private void registerPublicFirstBoard() {
        List<ScreenerStrategyRuleResp> rules = new ArrayList<>();
        rules.add(textRule(ScreenerRuleTypeEnum.MARKET_BOARD, "MAIN_BOARD", "沪深主板", 10));
        rules.add(booleanRule(ScreenerRuleTypeEnum.EXCLUDE_ST, true, "排除 ST", 20));
        rules.add(integerRule(ScreenerRuleTypeEnum.LIMIT_UP_LEVEL, ScreenerOperatorEnum.EQ,
                1, null, "当日首板", 30));
        rules.add(textComparisonRule(ScreenerRuleTypeEnum.FIRST_SEAL_TIME, ScreenerOperatorEnum.LTE,
                "103000", "首次封板不晚于 10:30", 40));
        rules.add(textComparisonRule(ScreenerRuleTypeEnum.LAST_SEAL_TIME, ScreenerOperatorEnum.LTE,
                "143000", "最后封板不晚于 14:30", 50));
        rules.add(integerRule(ScreenerRuleTypeEnum.BREAK_COUNT, ScreenerOperatorEnum.LTE,
                2, null, "炸板不超过 2 次", 60));
        rules.add(numberRule(ScreenerRuleTypeEnum.SEAL_AMOUNT, ScreenerOperatorEnum.GTE,
                "30000000", "封单金额至少 3000万", 70));
        rules.add(rangeRule(ScreenerRuleTypeEnum.TURNOVER_RATE, "3", "20", "换手率 3% - 20%", 80));
        rules.add(rangeRule(ScreenerRuleTypeEnum.AMOUNT, "300000000", "3000000000",
                "成交额 3亿 - 30亿", 90));
        rules.add(integerRule(ScreenerRuleTypeEnum.THEME_LINKAGE_COUNT, ScreenerOperatorEnum.GTE,
                2, null, "同题材涨停至少 2 家", 100));
        register("PUBLIC_FIRST_BOARD_DISPERSION", "公开首板分散模型",
                "基于公开首板方法的可解释候选过滤，关注封板质量、流动性和题材联动；仅用于选股，不包含下单规则。", rules);
    }

    private void registerSectorResonanceFirstBoard() {
        List<ScreenerStrategyRuleResp> rules = new ArrayList<>();
        rules.add(textRule(ScreenerRuleTypeEnum.MARKET_BOARD, "MAIN_BOARD", "沪深主板", 10));
        rules.add(booleanRule(ScreenerRuleTypeEnum.EXCLUDE_ST, true, "排除 ST", 20));
        rules.add(integerRule(ScreenerRuleTypeEnum.LIMIT_UP_LEVEL, ScreenerOperatorEnum.EQ,
                1, null, "当日首板", 30));
        rules.add(integerRule(ScreenerRuleTypeEnum.THEME_LINKAGE_COUNT, ScreenerOperatorEnum.GTE,
                3, null, "同题材涨停至少 3 家", 40));
        rules.add(textComparisonRule(ScreenerRuleTypeEnum.FIRST_SEAL_TIME, ScreenerOperatorEnum.LTE,
                "103000", "首次封板不晚于 10:30", 50));
        rules.add(textComparisonRule(ScreenerRuleTypeEnum.LAST_SEAL_TIME, ScreenerOperatorEnum.LTE,
                "140000", "最后封板不晚于 14:00", 60));
        rules.add(integerRule(ScreenerRuleTypeEnum.BREAK_COUNT, ScreenerOperatorEnum.LTE,
                1, null, "炸板不超过 1 次", 70));
        rules.add(numberRule(ScreenerRuleTypeEnum.SEAL_AMOUNT, ScreenerOperatorEnum.GTE,
                "30000000", "封单金额至少 3000万", 80));
        rules.add(rangeRule(ScreenerRuleTypeEnum.TURNOVER_RATE, "3", "20", "换手率 3% - 20%", 90));
        rules.add(rangeRule(ScreenerRuleTypeEnum.AMOUNT, "300000000", "3000000000",
                "成交额 3亿 - 30亿", 100));
        register("SECTOR_RESONANCE_FIRST_BOARD", "板块共振首板",
                "用同题材多股涨停确认板块联动，再筛选早封、少炸板且流动性适中的主板首板。", rules);
    }

    private void registerLowPositionFirstBoard() {
        List<ScreenerStrategyRuleResp> rules = new ArrayList<>();
        rules.add(textRule(ScreenerRuleTypeEnum.MARKET_BOARD, "MAIN_BOARD", "沪深主板", 10));
        rules.add(booleanRule(ScreenerRuleTypeEnum.EXCLUDE_ST, true, "排除 ST", 20));
        rules.add(numberRule(ScreenerRuleTypeEnum.PRICE_POSITION, ScreenerOperatorEnum.LTE,
                "30", "最新价位于近 120 日价格区间下方 30%", 30, 120));
        rules.add(integerRule(ScreenerRuleTypeEnum.LIMIT_UP_LEVEL, ScreenerOperatorEnum.EQ,
                1, null, "当日首板", 40));
        rules.add(integerRule(ScreenerRuleTypeEnum.THEME_LINKAGE_COUNT, ScreenerOperatorEnum.GTE,
                2, null, "同题材涨停至少 2 家", 50));
        rules.add(textComparisonRule(ScreenerRuleTypeEnum.FIRST_SEAL_TIME, ScreenerOperatorEnum.LTE,
                "140000", "首次封板不晚于 14:00", 60));
        rules.add(integerRule(ScreenerRuleTypeEnum.BREAK_COUNT, ScreenerOperatorEnum.LTE,
                2, null, "炸板不超过 2 次", 70));
        rules.add(rangeRule(ScreenerRuleTypeEnum.TURNOVER_RATE, "3", "20", "换手率 3% - 20%", 80));
        rules.add(rangeRule(ScreenerRuleTypeEnum.AMOUNT, "300000000", "3000000000",
                "成交额 3亿 - 30亿", 90));
        register("LOW_POSITION_FIRST_BOARD", "低位首板",
                "在阶段低位中筛选有题材联动、换手和成交额适中的主板首板，降低纯高位接力暴露。", rules);
    }

    private void registerLimitUpComeback() {
        List<ScreenerStrategyRuleResp> rules = new ArrayList<>();
        rules.add(textRule(ScreenerRuleTypeEnum.MARKET_BOARD, "MAIN_BOARD", "沪深主板", 10));
        rules.add(booleanRule(ScreenerRuleTypeEnum.EXCLUDE_ST, true, "排除 ST", 20));
        rules.add(integerRule(ScreenerRuleTypeEnum.DAYS_SINCE_LIMIT_UP, ScreenerOperatorEnum.GTE,
                2, 10, "距最近涨停至少 2 个交易日", 30));
        rules.add(integerRule(ScreenerRuleTypeEnum.DAYS_SINCE_LIMIT_UP, ScreenerOperatorEnum.LTE,
                10, 10, "距最近涨停不超过 10 个交易日", 40));
        rules.add(rangeRule(ScreenerRuleTypeEnum.CLOSE_MA_DISTANCE_PCT, "0", "5",
                "收盘位于 MA10 上方 0% - 5%", 50, 10));
        rules.add(numberRule(ScreenerRuleTypeEnum.VOLUME_MA_RATIO, ScreenerOperatorEnum.GTE,
                "120", "最新成交量至少为前 5 日均量的 120%", 60, 5));
        rules.add(booleanRule(ScreenerRuleTypeEnum.BREAKOUT_PREVIOUS_HIGH, true,
                "收盘突破前 3 日高点", 70, 3));
        rules.add(rangeRule(ScreenerRuleTypeEnum.PCT_CHG, "1", "7", "当日涨幅 1% - 7%", 80));
        rules.add(rangeRule(ScreenerRuleTypeEnum.AMOUNT, "300000000", "5000000000",
                "成交额 3亿 - 50亿", 90));
        rules.add(rangeRule(ScreenerRuleTypeEnum.ATR_PCT, "1", "6", "ATR14 占现价 1% - 6%", 100));
        register("LIMIT_UP_COMEBACK", "涨停回马枪",
                "筛选主板前期涨停后经过整理、重新放量突破短期前高且仍站在 MA10 上方的候选。",
                ScreenerRunModeEnum.CLOSE.getCode(), rules);
    }

    private void registerVolumeContractionPullback() {
        List<ScreenerStrategyRuleResp> rules = new ArrayList<>();
        rules.add(booleanRule(ScreenerRuleTypeEnum.EXCLUDE_ST, true, "排除 ST", 10));
        rules.add(booleanRule(ScreenerRuleTypeEnum.MA_BULLISH_ALIGNMENT, true,
                "MA5 > MA10 > MA20", 20));
        rules.add(rangeRule(ScreenerRuleTypeEnum.CLOSE_MA_DISTANCE_PCT, "0", "3",
                "收盘位于 MA10 上方 0% - 3%", 30, 10));
        rules.add(rangeRule(ScreenerRuleTypeEnum.VOLUME_MA_RATIO, "30", "80",
                "最新成交量为前 5 日均量的 30% - 80%", 40, 5));
        rules.add(numberRule(ScreenerRuleTypeEnum.RS20, ScreenerOperatorEnum.GTE,
                "2", "20 日相对沪深300强度至少 2%", 50));
        rules.add(rangeRule(ScreenerRuleTypeEnum.ATR_PCT, "1", "5", "ATR14 占现价 1% - 5%", 60));
        rules.add(rangeRule(ScreenerRuleTypeEnum.PCT_CHG, "-2", "3", "当日涨幅 -2% - 3%", 70));
        register("VOLUME_CONTRACTION_PULLBACK", "缩量回踩",
                "在均线多头和相对强势前提下，筛选缩量回踩 MA10 但仍收在均线上方的候选。",
                ScreenerRunModeEnum.CLOSE.getCode(), rules);
    }

    private void register(String key, String name, String description, List<ScreenerStrategyRuleResp> rules) {
        register(key, name, description, ScreenerRunModeEnum.REALTIME.getCode(), rules);
    }

    private void register(String key, String name, String description, String runMode,
                          List<ScreenerStrategyRuleResp> rules) {
        templateMap.put(key, ScreenerStrategyResp.builder()
                .templateKey(key)
                .name(name)
                .description(description)
                .sourceType("SYSTEM_TEMPLATE")
                .runMode(runMode)
                .enabled(true)
                .sortNo(templateMap.size() * 10 + 10)
                .versionNo(1)
                .template(true)
                .editable(false)
                .disclaimer(DISCLAIMER)
                .rules(rules)
                .build());
    }

    private ScreenerStrategyRuleResp rangeRule(ScreenerRuleTypeEnum type, String min, String max,
                                                String summary, int sortNo) {
        return rangeRule(type, min, max, summary, sortNo, null);
    }

    private ScreenerStrategyRuleResp rangeRule(ScreenerRuleTypeEnum type, String min, String max,
                                                String summary, int sortNo, Integer lookbackDays) {
        return rule(type, ScreenerOperatorEnum.BETWEEN, new BigDecimal(min), new BigDecimal(max),
                null, null, null, lookbackDays, summary, sortNo);
    }

    private ScreenerStrategyRuleResp numberRule(ScreenerRuleTypeEnum type, ScreenerOperatorEnum operator,
                                                 String value, String summary, int sortNo) {
        return numberRule(type, operator, value, summary, sortNo, null);
    }

    private ScreenerStrategyRuleResp numberRule(ScreenerRuleTypeEnum type, ScreenerOperatorEnum operator,
                                                 String value, String summary, int sortNo, Integer lookbackDays) {
        return rule(type, operator, new BigDecimal(value), null, null, null, null,
                lookbackDays, summary, sortNo);
    }

    private ScreenerStrategyRuleResp integerRule(ScreenerRuleTypeEnum type, ScreenerOperatorEnum operator,
                                                  Integer value, Integer lookbackDays, String summary, int sortNo) {
        return rule(type, operator, null, null, value, null, null, lookbackDays, summary, sortNo);
    }

    private ScreenerStrategyRuleResp textRule(ScreenerRuleTypeEnum type, String value,
                                               String summary, int sortNo) {
        return rule(type, ScreenerOperatorEnum.EQ, null, null, null, value, null,
                null, summary, sortNo);
    }

    private ScreenerStrategyRuleResp textComparisonRule(ScreenerRuleTypeEnum type,
                                                         ScreenerOperatorEnum operator, String value,
                                                         String summary, int sortNo) {
        return rule(type, operator, null, null, null, value, null, null, summary, sortNo);
    }

    private ScreenerStrategyRuleResp booleanRule(ScreenerRuleTypeEnum type, boolean value,
                                                  String summary, int sortNo) {
        return booleanRule(type, value, summary, sortNo, null);
    }

    private ScreenerStrategyRuleResp booleanRule(ScreenerRuleTypeEnum type, boolean value,
                                                  String summary, int sortNo, Integer lookbackDays) {
        return rule(type, ScreenerOperatorEnum.EQ, null, null, null, null, value,
                lookbackDays, summary, sortNo);
    }

    private ScreenerStrategyRuleResp rule(ScreenerRuleTypeEnum type, ScreenerOperatorEnum operator,
                                           BigDecimal minValue, BigDecimal maxValue, Integer intValue,
                                           String textValue, Boolean boolValue, Integer lookbackDays,
                                           String summary, int sortNo) {
        return ScreenerStrategyRuleResp.builder()
                .ruleType(type.getCode())
                .ruleName(type.getDesc())
                .operatorCode(operator.getCode())
                .operatorName(operator.getDesc())
                .minValue(minValue)
                .maxValue(maxValue)
                .intValue(intValue)
                .textValue(textValue)
                .boolValue(boolValue)
                .lookbackDays(lookbackDays)
                .sortNo(sortNo)
                .summary(summary)
                .build();
    }
}
