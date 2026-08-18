package com.awe.apex.quant.screener;

import com.awe.apex.quant.domain.dto.ScreenerStrategyGuideResp;
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
        registerPublicFirstBoard();
        registerMainBoardAcceptance();
        registerSectorResonanceFirstBoard();
        registerLowPositionFirstBoard();
        registerLimitUpComeback();
        registerVolumeContractionPullback();
        registerSwingTrendPullback();
        registerRangeGridCandidate();
        registerBreakoutVolume();
        registerRelativeStrengthTrend();
        registerLowPositionVolume();
        registerMidCapValuation();
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
                "主板强势股的实时换手、量比、涨幅、近期涨停和分时均价承接联合筛选。", rules,
                guide("盘中承接",
                        "先找近期被资金验证过、当天温和上涨且有量的中小市值主板，再用分时均价线判断买盘是否持续承接。",
                        "重点不是追 3% - 5% 的涨幅，而是确认上涨过程中的抛压被持续接住。价格稳在均价线上是承接证据，不等于必然继续上涨。",
                        "主线方向清晰、市场风险偏好不弱、个股不是一字加速的交易日。",
                        List.of("盘前用近 20 日涨停记录和 50亿 - 200亿总市值建立基础候选。",
                                "09:35 后观察换手率 5% - 10%、量比大于 1、涨幅 3% - 5% 是否同时成立。",
                                "只保留绝大部分时间在分时均价线上、短暂跌破后能快速收回的股票。",
                                "结合板块强弱等待回踩均价不破或再次放量突破分时高点，避免在直线拉升末端追价。"),
                        List.of("跌破均价超过 3 分钟且不能收回时，承接逻辑已经弱化。",
                                "板块同步转弱、量比快速回落或冲高放量滞涨时不勉强参与。",
                                "这是盘中候选过滤，不是买入保证；单票仓位和退出条件必须预先确定。")));
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
                "用实时量比和阶段涨幅确认价格突破，适合继续人工检查突破位置。", rules,
                guide("短线突破",
                        "找已经向上突破、同时有新增成交量配合的股票，避免只看价格不看资金。",
                        "有效突破需要价格和成交量互相确认；没有量的突破容易回落，放出巨量但涨不动也可能是抛压。",
                        "指数稳定、题材或行业正在扩散、前期平台边界清晰的阶段。",
                        List.of("先确认突破的是整理平台或阶段高点，而不是连续大涨后的随意新高。",
                                "比较量比、当日成交额和突破前均量，确认增量资金确实进入。",
                                "等待突破站稳或回踩平台不破后再评估，不把瞬时冲高当成确认。"),
                        List.of("跌回突破位且放量时，突破逻辑失效。",
                                "高位巨量、长上影或板块没有联动时降低优先级。")));
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
                "在相对沪深300走强的股票中筛选趋势连续、波动不过度的候选。", rules,
                guide("趋势跟随",
                        "不猜最低点，只跟踪那些比大盘更强、上涨节奏相对稳定的股票。",
                        "强者恒强有时会延续，但必须用波动率约束追高风险；相对强度下降通常比绝对涨跌更早暴露退潮。",
                        "指数震荡向上或结构性行情持续、领涨方向没有明显退潮时。",
                        List.of("先用 20 日相对强度找到持续跑赢沪深300的股票。",
                                "检查均线和上涨连续性，优先选择节奏稳定而非单日脉冲。",
                                "在回踩关键均线或缩量整理后观察趋势是否恢复。"),
                        List.of("相对强度转负、趋势均线被有效跌破时停止沿用原逻辑。",
                                "连续加速后的高波动阶段不宜机械追随。")));
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
                "从阶段低位中筛选温和反弹并出现放量的候选，避免追逐已大幅拉升标的。", rules,
                guide("低位反转",
                        "在长期位置较低的股票里，寻找第一次出现明显增量资金和温和反弹的候选。",
                        "低位本身不是理由，真正有意义的是低位出现资金行为变化；放量后还要观察价格能否摆脱原有下降结构。",
                        "市场完成快速杀跌后开始修复，或行业出现基本面和资金面的边际变化时。",
                        List.of("先确认股价处于近 120 日区间下方 30%，排除已经大幅拉升的股票。",
                                "观察量比至少 1.5 且近 5 日涨幅温和，避免单日透支。",
                                "继续检查是否突破下降趋势线或形成更高的低点。"),
                        List.of("低位可以继续创新低，不能把便宜等同于安全。",
                                "放量下跌、利空驱动或基本面恶化时不按反转处理。")));
    }

    private void registerMidCapValuation() {
        List<ScreenerStrategyRuleResp> rules = new ArrayList<>();
        rules.add(booleanRule(ScreenerRuleTypeEnum.EXCLUDE_ST, true, "排除 ST", 10));
        rules.add(rangeRule(ScreenerRuleTypeEnum.PE_TTM, "5", "35", "滚动市盈率 5 - 35", 20));
        rules.add(rangeRule(ScreenerRuleTypeEnum.PB, "0.5", "4", "市净率 0.5 - 4", 30));
        rules.add(rangeRule(ScreenerRuleTypeEnum.TOTAL_MV, "5000000000", "30000000000",
                "总市值 50亿 - 300亿", 40));
        register("MID_CAP_VALUATION", "中盘估值筛选",
                "按市盈率、市净率和中等市值范围过滤候选，不代表低估或买入结论。", rules,
                guide("估值筛选",
                        "先用市盈率、市净率和市值范围缩小研究名单，再回到行业和公司质量判断是否真的便宜。",
                        "估值指标只能做入口，低估值可能来自周期顶部、盈利下滑或治理风险，不能单独构成买入理由。",
                        "盈利相对稳定、财务口径可比、估值能够解释的成熟行业。",
                        List.of("用 PE、PB 和总市值剔除明显不符合研究范围的股票。",
                                "横向比较同行，纵向比较公司历史估值分位。",
                                "复核盈利质量、现金流、负债和未来两年利润假设。"),
                        List.of("亏损、强周期和一次性收益公司不适合机械使用 PE。",
                                "估值便宜但盈利预期持续下修时要重新计算安全边际。")));
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
        register("PUBLIC_FIRST_BOARD_DISPERSION", "北京炒家首板（公开规则版）",
                "参考北京炒家公开分享的首板分仓思路，筛选封板质量、流动性和题材联动较好的当日主板首板。", rules,
                guide("短线首板",
                        "把当天第一次涨停的股票作为候选，用早封、少炸板、封单质量和题材联动筛掉弱板，再用分仓降低单只股票的不确定性。",
                        "精髓不是猜下一只妖股，而是在大量首板中重复执行有统计优势的过滤和分仓纪律，赚取组合层面的次日溢价概率。",
                        "短线情绪回暖、首板封板率和次日溢价改善、市场有清晰主线或新题材发酵时。",
                        List.of("只看沪深主板非 ST 的当日首板，先确认同题材至少有 2 家涨停。",
                                "优先观察 10:30 前首封、炸板不超过 2 次、封单和成交额适中的股票。",
                                "封板稳定后再纳入候选，不在尚未封住时把它当成首板确认。",
                                "采用多票分仓思路控制单票暴露，次日根据竞价和板块强弱处理，不把短线交易拖成被动长持。"),
                        List.of("尾盘偷板、孤立涨停、反复炸板和高位加速板要降低优先级。",
                                "次日明显不及预期时按预案退出，不用补仓摊低成本替代止损。",
                                "名称仅指公开方法的规则化整理，并非本人授权或完整实盘复刻。")));
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
                "用同题材多股涨停确认板块联动，再筛选早封、少炸板且流动性适中的主板首板。", rules,
                guide("短线首板",
                        "先确认一个题材不是单只股票独涨，再从同题材首板里挑封板更早、更稳的候选。",
                        "板块联动能够分散个股偶然性；真正的强题材通常会有多只股票同步响应，而不是只有一只孤板。",
                        "新题材集中爆发、同方向涨停梯队开始形成、市场成交活跃时。",
                        List.of("确认同题材至少 3 家涨停，并检查领涨股、跟风股层次是否清晰。",
                                "在板块内比较首封时间、炸板次数、封单金额和成交额。",
                                "优先选择辨识度高且封板过程稳定的首板，不机械选择涨停数量最多的题材。"),
                        List.of("题材消息无法证实、板块冲高回落或只剩单只硬封时停止使用。",
                                "同题材个股高度同涨同跌，分仓不能消除板块级风险。")));
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
                "在阶段低位中筛选有题材联动、换手和成交额适中的主板首板，降低纯高位接力暴露。", rules,
                guide("低位首板",
                        "在长期位置较低、尚未连续炒作的股票中寻找第一次涨停，观察是否出现新的资金和题材驱动。",
                        "低位降低了高位筹码兑现压力，但只有位置低不够，首板质量和板块联动仍然决定次日预期。",
                        "低位板块出现政策、业绩或行业催化，市场开始高低切换时。",
                        List.of("确认股价位于近 120 日区间下方 30%，且当日为主板首板。",
                                "检查同题材至少 2 家涨停、换手和成交额能够支持正常进出。",
                                "结合首封时间和炸板次数判断资金态度，避免只因低位就降低质量要求。"),
                        List.of("长期阴跌股票的首次涨停可能只是超跌反抽。",
                                "基本面重大风险、减持或退市风险不能被低位标签掩盖。")));
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
                ScreenerRunModeEnum.CLOSE.getCode(), rules,
                guide("短线回踩",
                        "找前期涨停后没有立刻走坏、经过几天整理又重新放量突破的股票。",
                        "第一次涨停证明资金关注，整理阶段完成换手；二次放量突破才是回马枪成立的关键，而不是看到历史涨停就追。",
                        "热点仍有反复、前期涨停股回调有承接、市场不是单边退潮时。",
                        List.of("筛选 2 - 10 个交易日前出现涨停、仍站在 MA10 上方的股票。",
                                "等待成交量恢复到前 5 日均量 120% 以上，并突破近 3 日高点。",
                                "结合板块回流和收盘位置确认，不只看盘中瞬时突破。"),
                        List.of("跌破整理平台或 MA10 后不能快速修复，回马枪结构失效。",
                                "前期涨停来自一次性消息、板块已经退潮时慎用。")));
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
                ScreenerRunModeEnum.CLOSE.getCode(), rules,
                guide("趋势回踩",
                        "在上涨趋势里等一次成交量缩小的回调，只关注回调没有破坏趋势的股票。",
                        "缩量说明主动抛压有限，MA10 提供观察锚点；真正的买点仍需要回踩企稳和量价重新转强确认。",
                        "指数和行业趋势尚在、强势股从加速转入正常整理时。",
                        List.of("先确认 MA5、MA10、MA20 多头排列且相对沪深300保持强势。",
                                "等待成交量缩至前 5 日均量的 30% - 80%，收盘仍在 MA10 上方。",
                                "后续观察止跌 K 线或放量重新站稳短期高点，再决定是否纳入交易计划。"),
                        List.of("放量跌破 MA10 或均线开始粘合向下时，不再视为健康回踩。",
                                "缩量也可能代表无人承接，要结合价格止跌而不是只看量。")));
    }

    private void registerSwingTrendPullback() {
        List<ScreenerStrategyRuleResp> rules = new ArrayList<>();
        rules.add(booleanRule(ScreenerRuleTypeEnum.EXCLUDE_ST, true, "排除 ST", 10));
        rules.add(booleanRule(ScreenerRuleTypeEnum.MA_BULLISH_ALIGNMENT, true,
                "MA5 > MA10 > MA20", 20));
        rules.add(rangeRule(ScreenerRuleTypeEnum.RANGE_RETURN, "5", "30",
                "近 60 个交易日涨幅 5% - 30%", 30, 60));
        rules.add(rangeRule(ScreenerRuleTypeEnum.CLOSE_MA_DISTANCE_PCT, "0", "6",
                "收盘位于 MA20 上方 0% - 6%", 40, 20));
        rules.add(numberRule(ScreenerRuleTypeEnum.RS20, ScreenerOperatorEnum.GTE,
                "2", "20 日相对沪深300强度至少 2%", 50));
        rules.add(rangeRule(ScreenerRuleTypeEnum.ATR_PCT, "1.5", "5",
                "ATR14 占现价 1.5% - 5%", 60));
        rules.add(rangeRule(ScreenerRuleTypeEnum.VOLUME_MA_RATIO, "60", "180",
                "最新成交量为前 5 日均量的 60% - 180%", 70, 5));
        rules.add(rangeRule(ScreenerRuleTypeEnum.TOTAL_MV, "5000000000", "100000000000",
                "总市值 50亿 - 1000亿", 80));
        register("SWING_TREND_PULLBACK", "趋势波段回踩",
                "筛选中期趋势向上、相对强势且回踩 MA20 未破的波段候选，不追求单日爆发。",
                ScreenerRunModeEnum.CLOSE.getCode(), rules,
                guide("趋势波段",
                        "跟随已经形成的中期上升趋势，在回调靠近 MA20 时寻找下一段行情，而不是每天追逐涨停。",
                        "波段的核心是让盈利跟随趋势扩张、在趋势破坏时退出。买得最低并不重要，方向正确和持有纪律更重要。",
                        "指数没有系统性下跌、行业景气或资金趋势能够持续数周的阶段。",
                        List.of("先确认 MA5、MA10、MA20 多头排列，近 60 日保持温和上涨且跑赢沪深300。",
                                "等待价格回到 MA20 上方 0% - 6%，成交量不过度放大，避免追在短期加速点。",
                                "用近期平台低点或 MA20 作为结构失效参考，盈利后沿上升趋势逐步移动保护位。",
                                "分批建仓和分批止盈，减少一次判断对结果的影响。"),
                        List.of("收盘有效跌破 MA20、均线转为空头或行业趋势反转时停止持有原假设。",
                                "波段持有周期更长，必须预留正常波动空间，不能用超短线噪声频繁止损。",
                                "财报、减持和行业政策等事件风险需要单独检查。")));
    }

    private void registerRangeGridCandidate() {
        List<ScreenerStrategyRuleResp> rules = new ArrayList<>();
        rules.add(booleanRule(ScreenerRuleTypeEnum.EXCLUDE_ST, true, "排除 ST", 10));
        rules.add(rangeRule(ScreenerRuleTypeEnum.TOTAL_MV, "5000000000", "100000000000",
                "总市值 50亿 - 1000亿", 20));
        rules.add(rangeRule(ScreenerRuleTypeEnum.RANGE_RETURN, "-8", "8",
                "近 20 个交易日涨幅 -8% - 8%", 30, 20));
        rules.add(rangeRule(ScreenerRuleTypeEnum.PRICE_POSITION, "25", "75",
                "最新价位于近 60 日区间中部 25% - 75%", 40, 60));
        rules.add(rangeRule(ScreenerRuleTypeEnum.ATR_PCT, "2", "6",
                "ATR14 占现价 2% - 6%", 50));
        rules.add(rangeRule(ScreenerRuleTypeEnum.CLOSE_MA_DISTANCE_PCT, "-6", "6",
                "收盘偏离 MA20 不超过 6%", 60, 20));
        rules.add(rangeRule(ScreenerRuleTypeEnum.VOLUME_MA_RATIO, "60", "160",
                "最新成交量为前 5 日均量的 60% - 160%", 70, 5));
        rules.add(rangeRule(ScreenerRuleTypeEnum.AMOUNT, "200000000", "5000000000",
                "成交额 2亿 - 50亿", 80));
        rules.add(rangeRule(ScreenerRuleTypeEnum.PCT_CHG, "-3", "3",
                "当日涨幅 -3% - 3%", 90));
        register("RANGE_GRID_CANDIDATE", "震荡网格候选",
                "筛选区间震荡、波动适中且流动性可用的网格候选；只识别标的，不自动生成或执行网格订单。",
                ScreenerRunModeEnum.CLOSE.getCode(), rules,
                guide("震荡网格",
                        "把一段明确震荡区间切成若干价格格子，价格每跌一格买一份、每涨一格卖一份，用来反复收集波动。",
                        "网格赚的是来回震荡，不是单边方向。区间、格距、底仓和资金上限必须先固定；一旦走成单边趋势，原网格就应停止。",
                        "价格长期围绕中枢震荡、波动稳定、成交活跃且没有明显单边趋势的股票或 ETF。",
                        List.of("用近 60 日高低点和成交密集区确定网格上下边界，边界外不继续无限加仓。",
                                "格距应覆盖手续费和滑点，可参考 ATR 设置，波动越大格距越宽。",
                                "预先确定网格数量、每格资金和最大总仓位，保留现金应对向下触发。",
                                "到达上边界、跌破下边界或基本面发生变化时重新评估区间，不能让旧参数永久运行。"),
                        List.of("单边下跌会不断买入并迅速耗尽现金，是网格最大的风险。",
                                "单边上涨可能过早卖空底仓，错过趋势收益。",
                                "小格距可能被手续费和滑点吞噬；本模板只筛候选，不代表已完成网格参数验证。")));
    }

    private void register(String key, String name, String description, List<ScreenerStrategyRuleResp> rules,
                          ScreenerStrategyGuideResp guide) {
        register(key, name, description, ScreenerRunModeEnum.REALTIME.getCode(), rules, guide);
    }

    private void register(String key, String name, String description, String runMode,
                          List<ScreenerStrategyRuleResp> rules, ScreenerStrategyGuideResp guide) {
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
                .guide(guide)
                .rules(rules)
                .build());
    }

    private ScreenerStrategyGuideResp guide(String category, String plainExplanation, String coreIdea,
                                             String suitableMarket, List<String> executionSteps,
                                             List<String> riskNotes) {
        return ScreenerStrategyGuideResp.builder()
                .category(category)
                .plainExplanation(plainExplanation)
                .coreIdea(coreIdea)
                .suitableMarket(suitableMarket)
                .executionSteps(executionSteps)
                .riskNotes(riskNotes)
                .build();
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
