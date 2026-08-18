package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.bo.DashboardCommandContextBO;
import com.awe.apex.quant.domain.dto.CommandDirectionItemResp;
import com.awe.apex.quant.domain.dto.CommandEvidenceItemResp;
import com.awe.apex.quant.domain.dto.CommandWatchConditionResp;
import com.awe.apex.quant.domain.dto.DashboardCommandResp;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.dto.MarketTipItem;
import com.awe.apex.quant.domain.dto.MorningBriefingResp;
import com.awe.apex.quant.domain.dto.NewsPulseResp;
import com.awe.apex.quant.domain.dto.ObservePoolResp;
import com.awe.apex.quant.domain.dto.OperationGuideItemResp;
import com.awe.apex.quant.domain.dto.PreMarketSummaryResp;
import com.awe.apex.quant.domain.dto.TodayOperationGuideResp;
import com.awe.apex.quant.domain.enums.DashboardCommandPhaseEnum;
import com.awe.apex.quant.domain.enums.DashboardCommandStatusEnum;
import com.awe.apex.quant.domain.enums.OperationGuideCodeEnum;
import com.awe.apex.quant.domain.enums.OperationGuideStatusEnum;
import com.awe.apex.quant.domain.enums.OperationTargetTypeEnum;
import com.awe.apex.quant.market.TradingCalendar;
import com.awe.apex.quant.service.IDashboardCommandService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 看板盘前指挥规则服务实现。
 */
@Service
public class DashboardCommandServiceImpl implements IDashboardCommandService {

    private static final LocalTime SESSION_START = LocalTime.of(9, 30);
    private static final LocalTime SESSION_END = LocalTime.of(15, 0);

    /**
     * 根据已有市场和用户数据生成盘前总结与今日操作指引。
     *
     * @param context 指挥规则计算上下文
     * @return 盘前总结与今日操作指引
     */
    @Override
    public DashboardCommandResp build(DashboardCommandContextBO context) {
        DashboardCommandContextBO commandContext = Objects.nonNull(context)
                ? context : new DashboardCommandContextBO();
        LocalDateTime generatedAt = Objects.nonNull(commandContext.getCurrentTime())
                ? commandContext.getCurrentTime() : LocalDateTime.now();

        // 1. 统一目标交易日和严格数据门禁，避免把上一交易日数据误标为当日实时
        LocalDate currentDate = generatedAt.toLocalDate();
        boolean tradingDay = TradingCalendar.isTradingDay(currentDate);
        LocalDate tradeDate = tradingDay ? currentDate : TradingCalendar.nextTradingDay(currentDate);
        LocalDate expectedMarketDate = TradingCalendar.prevTradingDay(tradeDate);
        DashboardCommandPhaseEnum phase = resolvePhase(generatedAt, tradingDay);
        DashboardCommandStatusEnum status = resolveStatus(commandContext, tradeDate,
                expectedMarketDate, phase);
        MarketBriefingResp marketBriefing = commandContext.getMarketBriefing();
        LocalDate marketDataAsOf = Objects.nonNull(marketBriefing) ? marketBriefing.getAsOf() : null;
        DecisionTodayResp decision = commandContext.getDecision();
        LocalDate decisionDataAsOf = Objects.nonNull(decision) ? decision.getDataAsOf() : null;

        // 2. 以 A 股立场为基线生成总结，再按风险、新仓、失效条件生成用户动作
        PreMarketSummaryResp preMarketSummary = buildPreMarketSummary(commandContext, status);
        TodayOperationGuideResp operationGuide = buildOperationGuide(commandContext, tradeDate,
                expectedMarketDate, phase, status);

        // 3. 同步返回目标日、各数据截止时间和可信状态，供前端明确展示
        return DashboardCommandResp.builder()
                .tradeDate(tradeDate)
                .marketDataAsOf(marketDataAsOf)
                .decisionDataAsOf(decisionDataAsOf)
                .generatedAt(generatedAt)
                .phase(phase.getCode())
                .status(status.getCode())
                .dataLevel(Objects.nonNull(marketBriefing) ? marketBriefing.getDataLevel() : "RED")
                .preMarketSummary(preMarketSummary)
                .operationGuide(operationGuide)
                .build();
    }

    private DashboardCommandPhaseEnum resolvePhase(LocalDateTime generatedAt, boolean tradingDay) {
        if (!tradingDay) {
            return DashboardCommandPhaseEnum.NON_TRADING_DAY;
        }
        LocalTime currentTime = generatedAt.toLocalTime();
        if (currentTime.isBefore(SESSION_START)) {
            return DashboardCommandPhaseEnum.PRE_MARKET;
        }
        if (!currentTime.isAfter(SESSION_END)) {
            return DashboardCommandPhaseEnum.IN_SESSION;
        }
        return DashboardCommandPhaseEnum.AFTER_CLOSE;
    }

    private DashboardCommandStatusEnum resolveStatus(DashboardCommandContextBO context,
                                                     LocalDate tradeDate,
                                                     LocalDate expectedMarketDate,
                                                     DashboardCommandPhaseEnum phase) {
        MarketBriefingResp marketBriefing = context.getMarketBriefing();
        if (Objects.isNull(marketBriefing)
                || Objects.isNull(marketBriefing.getAsOf())
                || "RED".equalsIgnoreCase(marketBriefing.getDataLevel())) {
            return DashboardCommandStatusEnum.BLOCKED;
        }
        if (marketBriefing.getAsOf().isBefore(expectedMarketDate)) {
            return DashboardCommandStatusEnum.STALE;
        }
        MorningBriefingResp morningBriefing = context.getMorningBriefing();
        if (Objects.nonNull(morningBriefing)
                && Objects.nonNull(morningBriefing.getTradeDate())
                && !tradeDate.equals(morningBriefing.getTradeDate())) {
            return DashboardCommandStatusEnum.STALE;
        }
        if (Boolean.TRUE.equals(context.getDecisionRunning())) {
            return DashboardCommandStatusEnum.GENERATING;
        }

        DecisionTodayResp decision = context.getDecision();
        boolean partial = Objects.isNull(morningBriefing)
                || Objects.isNull(morningBriefing.getTradeDate())
                || Objects.isNull(decision)
                || "YELLOW".equalsIgnoreCase(marketBriefing.getDataLevel())
                || (Objects.nonNull(morningBriefing)
                && "YELLOW".equalsIgnoreCase(morningBriefing.getDataLevel()));
        if (Objects.nonNull(decision)
                && (Objects.isNull(decision.getActionDate())
                || !tradeDate.equals(decision.getActionDate())
                || !isDecisionFresh(decision, tradeDate, expectedMarketDate))) {
            partial = true;
        }
        if (DashboardCommandPhaseEnum.IN_SESSION.equals(phase)
                && marketBriefing.getAsOf().isBefore(tradeDate)) {
            partial = true;
        }
        return partial ? DashboardCommandStatusEnum.PARTIAL : DashboardCommandStatusEnum.READY;
    }

    private PreMarketSummaryResp buildPreMarketSummary(DashboardCommandContextBO context,
                                                       DashboardCommandStatusEnum status) {
        MarketBriefingResp marketBriefing = context.getMarketBriefing();
        MorningBriefingResp morningBriefing = context.getMorningBriefing();
        String headline = buildHeadline(marketBriefing, morningBriefing, status);

        List<CommandDirectionItemResp> opportunityItems = new ArrayList<>();
        Set<String> opportunityNames = new HashSet<>();
        if (Objects.nonNull(marketBriefing) && CollUtil.isNotEmpty(marketBriefing.getHotThemes())) {
            for (String hotTheme : marketBriefing.getHotThemes()) {
                if (StringUtils.isNotBlank(hotTheme) && opportunityNames.add(hotTheme.trim())) {
                    opportunityItems.add(CommandDirectionItemResp.builder()
                            .name(hotTheme.trim())
                            .reason("A股市场简报明确主线")
                            .build());
                }
                if (opportunityItems.size() >= 2) {
                    break;
                }
            }
        }
        NewsPulseResp newsPulse = Objects.nonNull(morningBriefing) ? morningBriefing.getNewsPulse() : null;
        if (opportunityItems.size() < 2 && Objects.nonNull(newsPulse)
                && CollUtil.isNotEmpty(newsPulse.getHotThemes())) {
            for (String hotTheme : newsPulse.getHotThemes()) {
                if (StringUtils.isNotBlank(hotTheme) && opportunityNames.add(hotTheme.trim())) {
                    opportunityItems.add(CommandDirectionItemResp.builder()
                            .name(hotTheme.trim())
                            .reason("消息面出现显式主题，仅作为候选方向")
                            .build());
                }
                if (opportunityItems.size() >= 2) {
                    break;
                }
            }
        }

        List<CommandDirectionItemResp> riskItems = new ArrayList<>();
        if (Objects.nonNull(marketBriefing)
                && Objects.nonNull(marketBriefing.getBreadthDown())
                && Objects.nonNull(marketBriefing.getBreadthUp())
                && marketBriefing.getBreadthDown() > marketBriefing.getBreadthUp()) {
            riskItems.add(CommandDirectionItemResp.builder()
                    .name("市场广度")
                    .reason("下跌家数多于上涨家数")
                    .build());
        }
        if (Objects.nonNull(marketBriefing)
                && Objects.nonNull(marketBriefing.getLimitDownCount())
                && marketBriefing.getLimitDownCount() > 0
                && riskItems.size() < 2) {
            riskItems.add(CommandDirectionItemResp.builder()
                    .name("跌停风险")
                    .reason("当前跌停 " + marketBriefing.getLimitDownCount() + " 家")
                    .build());
        }
        if (riskItems.size() < 2 && Objects.nonNull(newsPulse)
                && Objects.nonNull(newsPulse.getBearCount())
                && Objects.nonNull(newsPulse.getBullCount())
                && newsPulse.getBearCount() > newsPulse.getBullCount()) {
            riskItems.add(CommandDirectionItemResp.builder()
                    .name("消息面")
                    .reason("利空消息数量多于利好消息")
                    .build());
        }

        List<CommandEvidenceItemResp> evidenceItems = new ArrayList<>();
        if (Objects.nonNull(marketBriefing)) {
            String stance = StringUtils.isNotBlank(marketBriefing.getStance())
                    ? marketBriefing.getStance() : "未确定";
            String stanceValue = Objects.nonNull(marketBriefing.getStanceScore())
                    ? stance + " " + marketBriefing.getStanceScore() + "/100" : stance;
            evidenceItems.add(CommandEvidenceItemResp.builder()
                    .label("市场立场")
                    .value(stanceValue)
                    .signal(resolveStanceSignal(stance))
                    .build());
            if (Objects.nonNull(marketBriefing.getBreadthUp())
                    && Objects.nonNull(marketBriefing.getBreadthDown())) {
                evidenceItems.add(CommandEvidenceItemResp.builder()
                        .label("市场广度")
                        .value("涨 " + marketBriefing.getBreadthUp()
                                + " / 跌 " + marketBriefing.getBreadthDown())
                        .signal(marketBriefing.getBreadthUp() >= marketBriefing.getBreadthDown()
                                ? "POSITIVE" : "NEGATIVE")
                        .build());
            }
            if (Objects.nonNull(marketBriefing.getLimitUpCount())
                    || Objects.nonNull(marketBriefing.getLimitDownCount())) {
                int limitUpCount = Objects.nonNull(marketBriefing.getLimitUpCount())
                        ? marketBriefing.getLimitUpCount() : 0;
                int limitDownCount = Objects.nonNull(marketBriefing.getLimitDownCount())
                        ? marketBriefing.getLimitDownCount() : 0;
                evidenceItems.add(CommandEvidenceItemResp.builder()
                        .label("涨跌停")
                        .value("涨停 " + limitUpCount + " / 跌停 " + limitDownCount)
                        .signal(limitUpCount > limitDownCount ? "POSITIVE" : "NEGATIVE")
                        .build());
            }
        }
        if (Objects.nonNull(newsPulse) && evidenceItems.size() < 4) {
            int bullCount = Objects.nonNull(newsPulse.getBullCount()) ? newsPulse.getBullCount() : 0;
            int bearCount = Objects.nonNull(newsPulse.getBearCount()) ? newsPulse.getBearCount() : 0;
            evidenceItems.add(CommandEvidenceItemResp.builder()
                    .label("消息面")
                    .value("利好 " + bullCount + " / 利空 " + bearCount)
                    .signal(bullCount > bearCount ? "POSITIVE"
                            : bullCount < bearCount ? "NEGATIVE" : "NEUTRAL")
                    .build());
        } else if (Objects.nonNull(morningBriefing)
                && StringUtils.isNotBlank(morningBriefing.getSummary())
                && evidenceItems.size() < 4) {
            evidenceItems.add(CommandEvidenceItemResp.builder()
                    .label("隔夜背景")
                    .value(morningBriefing.getSummary())
                    .signal("NEUTRAL")
                    .build());
        }

        List<CommandWatchConditionResp> watchConditions = new ArrayList<>();
        if (Objects.nonNull(marketBriefing) && CollUtil.isNotEmpty(marketBriefing.getTips())) {
            for (MarketTipItem tip : marketBriefing.getTips()) {
                if (Objects.nonNull(tip) && StringUtils.isNotBlank(tip.getText())) {
                    watchConditions.add(CommandWatchConditionResp.builder()
                            .title("开盘观察")
                            .condition(tip.getText().trim())
                            .build());
                    break;
                }
            }
        }
        if (CollUtil.isEmpty(watchConditions)) {
            watchConditions.add(CommandWatchConditionResp.builder()
                    .title("恢复节奏")
                    .condition("市场立场改善且数据保持正常后，再恢复正常新仓节奏")
                    .build());
        }
        watchConditions.add(CommandWatchConditionResp.builder()
                .title("计划失效")
                .condition("数据过期或阻断、市场立场下调时停止新增动作")
                .build());

        return PreMarketSummaryResp.builder()
                .headline(headline)
                .opportunityItems(opportunityItems)
                .riskItems(riskItems)
                .evidenceItems(evidenceItems)
                .watchConditions(watchConditions)
                .build();
    }

    private String buildHeadline(MarketBriefingResp marketBriefing,
                                 MorningBriefingResp morningBriefing,
                                 DashboardCommandStatusEnum status) {
        if (DashboardCommandStatusEnum.BLOCKED.equals(status)) {
            return "关键数据不足，盘前结论不可用；补齐数据前只执行风险处置。";
        }
        if (DashboardCommandStatusEnum.STALE.equals(status)) {
            return "市场数据已过期，盘前结论不可执行；刷新数据前只保留风险处置。";
        }
        String stance = Objects.nonNull(marketBriefing) && StringUtils.isNotBlank(marketBriefing.getStance())
                ? marketBriefing.getStance().trim() : "防守";
        String stanceReason = Objects.nonNull(marketBriefing)
                && StringUtils.isNotBlank(marketBriefing.getStanceReason())
                ? marketBriefing.getStanceReason().trim() : "";
        if (stanceReason.length() > 18) {
            stanceReason = stanceReason.substring(0, 18) + "...";
        }
        String stanceReasonClause = StringUtils.isNotBlank(stanceReason)
                ? "，" + stanceReason : "";
        boolean hasOvernightContext = Objects.nonNull(morningBriefing)
                && StringUtils.isNotBlank(morningBriefing.getSummary());
        if ("进攻".equals(stance)) {
            return hasOvernightContext
                    ? "A股进攻基线不变" + stanceReasonClause
                    + "；隔夜信息只校准节奏，优先执行主线有效信号。"
                    : "A股进攻基线不变" + stanceReasonClause
                    + "；优先执行主线有效信号，并按失效条件控制节奏。";
        }
        if ("均衡".equals(stance)) {
            return hasOvernightContext
                    ? "A股均衡基线不变" + stanceReasonClause
                    + "；隔夜信息只校准节奏，按条件控制新仓。"
                    : "A股维持均衡基线" + stanceReasonClause
                    + "；等待主线和市场广度确认后再按条件新仓。";
        }
        return hasOvernightContext
                ? "A股防守基线不变" + stanceReasonClause
                + "；隔夜信息仅校准节奏，先处理风险、不抢开盘。"
                : "A股防守基线不变" + stanceReasonClause
                + "；先处理风险，等待市场广度和主线修复。";
    }

    private String resolveStanceSignal(String stance) {
        if ("进攻".equals(stance)) {
            return "POSITIVE";
        }
        if ("防守".equals(stance)) {
            return "NEGATIVE";
        }
        return "NEUTRAL";
    }

    private TodayOperationGuideResp buildOperationGuide(DashboardCommandContextBO context,
                                                        LocalDate tradeDate,
                                                        LocalDate expectedMarketDate,
                                                        DashboardCommandPhaseEnum phase,
                                                        DashboardCommandStatusEnum status) {
        MarketBriefingResp marketBriefing = context.getMarketBriefing();
        BigDecimal targetPositionMin = new BigDecimal("0.40");
        BigDecimal targetPositionMax = new BigDecimal("0.60");
        BigDecimal newPositionFactor = BigDecimal.ONE;
        if (DashboardCommandStatusEnum.BLOCKED.equals(status)
                || DashboardCommandStatusEnum.STALE.equals(status)) {
            targetPositionMin = BigDecimal.ZERO;
            targetPositionMax = new BigDecimal("0.10");
            newPositionFactor = new BigDecimal("0.40");
        } else if (Objects.nonNull(marketBriefing)
                && "防守".equals(marketBriefing.getStance())
                && (Objects.isNull(marketBriefing.getStanceScore())
                || marketBriefing.getStanceScore() <= 40)) {
            targetPositionMin = new BigDecimal("0.20");
            targetPositionMax = new BigDecimal("0.40");
            newPositionFactor = new BigDecimal("0.55");
        } else if (Objects.nonNull(marketBriefing)
                && "进攻".equals(marketBriefing.getStance())
                && Objects.nonNull(marketBriefing.getStanceScore())
                && marketBriefing.getStanceScore() >= 65) {
            targetPositionMin = new BigDecimal("0.60");
            targetPositionMax = new BigDecimal("0.80");
            newPositionFactor = new BigDecimal("1.10");
        }

        int freshTriggeredCount = countFreshTriggeredAlerts(context.getObserveAlerts(), expectedMarketDate);
        OperationGuideItemResp riskItem = buildRiskItem(context.getDecision(), tradeDate,
                expectedMarketDate, freshTriggeredCount);
        OperationGuideItemResp buyItem = buildBuyItem(context.getDecision(), tradeDate,
                expectedMarketDate, phase, status, newPositionFactor);
        OperationGuideItemResp invalidationItem = buildInvalidationItem(status);
        List<OperationGuideItemResp> items = new ArrayList<>();
        items.add(riskItem);
        items.add(buyItem);
        items.add(invalidationItem);

        String blockedReason = null;
        if (DashboardCommandStatusEnum.BLOCKED.equals(status)) {
            blockedReason = "市场数据缺失或等级为 RED，请先补齐数据";
        } else if (DashboardCommandStatusEnum.STALE.equals(status)) {
            blockedReason = "市场或晨报日期已过期，请先刷新数据";
        }
        String summary;
        if (OperationGuideStatusEnum.REQUIRED.getCode().equals(riskItem.getStatus())) {
            summary = "先处理 " + riskItem.getTargetCount() + " 项风险动作，再检查条件新仓。";
        } else if (OperationGuideStatusEnum.READY.getCode().equals(buyItem.getStatus())) {
            summary = "当前无优先风险动作，可按条件处理 "
                    + buyItem.getTargetCount() + " 个新仓候选。";
        } else {
            summary = "当前无优先风险动作，等待数据和可执行买点确认。";
        }
        return TodayOperationGuideResp.builder()
                .summary(summary)
                .targetPositionMin(targetPositionMin)
                .targetPositionMax(targetPositionMax)
                .newPositionFactor(newPositionFactor)
                .blockedReason(blockedReason)
                .items(items)
                .build();
    }

    private OperationGuideItemResp buildRiskItem(DecisionTodayResp decision,
                                                 LocalDate tradeDate,
                                                 LocalDate expectedMarketDate,
                                                 int freshTriggeredCount) {
        int sellCount = Objects.nonNull(decision) && Objects.nonNull(decision.getSellCount())
                ? Math.max(0, decision.getSellCount()) : 0;
        int targetCount = sellCount + freshTriggeredCount;
        boolean decisionFresh = isDecisionFresh(decision, tradeDate, expectedMarketDate);
        OperationGuideStatusEnum status;
        String conditionText;
        if ((sellCount > 0 && decisionFresh) || freshTriggeredCount > 0) {
            status = OperationGuideStatusEnum.REQUIRED;
            conditionText = "当前有 " + sellCount + " 个卖出项、"
                    + freshTriggeredCount + " 个已触发观察项";
        } else if (sellCount > 0) {
            status = OperationGuideStatusEnum.WAIT;
            conditionText = "卖出信号数据已旧，执行前必须重新复核";
        } else {
            status = OperationGuideStatusEnum.DONE;
            conditionText = "当前无卖出项或新鲜触发提醒";
        }
        return OperationGuideItemResp.builder()
                .priority(1)
                .code(OperationGuideCodeEnum.RISK_FIRST.getCode())
                .status(status.getCode())
                .title("风险处置")
                .actionText(status.equals(OperationGuideStatusEnum.DONE)
                        ? "当前无优先风险动作" : "优先处理卖出、止损和已触发观察项")
                .conditionText(conditionText)
                .targetCount(targetCount)
                .targetType(OperationTargetTypeEnum.POSITION.getCode())
                .build();
    }

    private OperationGuideItemResp buildBuyItem(DecisionTodayResp decision,
                                                LocalDate tradeDate,
                                                LocalDate expectedMarketDate,
                                                DashboardCommandPhaseEnum phase,
                                                DashboardCommandStatusEnum commandStatus,
                                                BigDecimal newPositionFactor) {
        int executableCount = Objects.nonNull(decision) && Objects.nonNull(decision.getExecutableCount())
                ? Math.max(0, decision.getExecutableCount()) : 0;
        OperationGuideStatusEnum status;
        String conditionText;
        if (DashboardCommandStatusEnum.BLOCKED.equals(commandStatus)
                || DashboardCommandStatusEnum.STALE.equals(commandStatus)) {
            status = OperationGuideStatusEnum.BLOCKED;
            conditionText = "市场数据不可用或过期，禁止新增仓";
        } else if (DashboardCommandStatusEnum.GENERATING.equals(commandStatus)) {
            status = OperationGuideStatusEnum.WAIT;
            conditionText = "新一轮决策正在生成，完成前不沿用旧候选";
        } else if (DashboardCommandPhaseEnum.NON_TRADING_DAY.equals(phase)) {
            status = OperationGuideStatusEnum.WAIT;
            conditionText = "当前为非交易日，仅准备下个交易日计划";
        } else if (Objects.isNull(decision)
                || Objects.isNull(decision.getActionDate())
                || !tradeDate.equals(decision.getActionDate())) {
            status = OperationGuideStatusEnum.WAIT;
            conditionText = "目标交易日决策尚未生成";
        } else if (!isDecisionFresh(decision, tradeDate, expectedMarketDate)) {
            status = OperationGuideStatusEnum.WAIT;
            conditionText = "决策数据已旧，等待重新生成";
        } else if (executableCount > 0) {
            status = OperationGuideStatusEnum.READY;
            conditionText = "仅处理通过准入门禁的候选，新仓系数 "
                    + newPositionFactor.stripTrailingZeros().toPlainString();
        } else {
            status = OperationGuideStatusEnum.WAIT;
            conditionText = "当前没有可执行候选，不将普通买入候选视为可执行";
        }
        return OperationGuideItemResp.builder()
                .priority(2)
                .code(OperationGuideCodeEnum.BUY_CONDITIONALLY.getCode())
                .status(status.getCode())
                .title("新仓计划")
                .actionText(status.equals(OperationGuideStatusEnum.READY)
                        ? "按条件处理主线匹配且可执行的候选" : "等待可执行买点")
                .conditionText(conditionText)
                .targetCount(executableCount)
                .targetType(OperationTargetTypeEnum.DECISION.getCode())
                .build();
    }

    private OperationGuideItemResp buildInvalidationItem(DashboardCommandStatusEnum commandStatus) {
        boolean invalid = DashboardCommandStatusEnum.BLOCKED.equals(commandStatus)
                || DashboardCommandStatusEnum.STALE.equals(commandStatus);
        return OperationGuideItemResp.builder()
                .priority(3)
                .code(invalid ? OperationGuideCodeEnum.REFRESH_DATA.getCode()
                        : OperationGuideCodeEnum.VIEW_CONTEXT.getCode())
                .status(invalid ? OperationGuideStatusEnum.BLOCKED.getCode()
                        : OperationGuideStatusEnum.WAIT.getCode())
                .title(invalid ? "刷新数据" : "失效条件")
                .actionText(invalid ? "停止新增动作并刷新数据" : "持续检查计划是否仍然有效")
                .conditionText("数据降级、市场立场下调或决策信号变化时停止新增动作")
                .targetCount(0)
                .targetType(OperationTargetTypeEnum.DATA.getCode())
                .build();
    }

    private boolean isDecisionFresh(DecisionTodayResp decision,
                                    LocalDate tradeDate,
                                    LocalDate expectedMarketDate) {
        return Objects.nonNull(decision)
                && Objects.nonNull(decision.getActionDate())
                && tradeDate.equals(decision.getActionDate())
                && Boolean.TRUE.equals(decision.getGenerated())
                && Objects.nonNull(decision.getDataAsOf())
                && !decision.getDataAsOf().isBefore(expectedMarketDate);
    }

    private int countFreshTriggeredAlerts(List<ObservePoolResp> observeAlerts,
                                          LocalDate expectedMarketDate) {
        if (CollUtil.isEmpty(observeAlerts)) {
            return 0;
        }
        int triggeredCount = 0;
        for (ObservePoolResp observeAlert : observeAlerts) {
            if (Objects.nonNull(observeAlert)
                    && "TRIGGERED".equals(observeAlert.getStatus())
                    && Objects.nonNull(observeAlert.getTriggeredAt())
                    && !observeAlert.getTriggeredAt().toLocalDate().isBefore(expectedMarketDate)) {
                triggeredCount++;
            }
        }
        return triggeredCount;
    }
}
