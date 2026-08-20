package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.bo.DashboardCommandContextBO;
import com.awe.apex.quant.domain.dto.CommandDirectionItemResp;
import com.awe.apex.quant.domain.dto.CommandWatchConditionResp;
import com.awe.apex.quant.domain.dto.DashboardCommandResp;
import com.awe.apex.quant.domain.dto.DecisionItemResp;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.dto.MorningBriefingResp;
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
import java.util.List;
import java.util.Objects;

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

        // 2. 按数据与决策状态生成结论，再按风险、新仓、失效条件生成用户动作
        PreMarketSummaryResp preMarketSummary = buildPreMarketSummary(commandContext, status,
                tradeDate, expectedMarketDate);
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
                                                       DashboardCommandStatusEnum status,
                                                       LocalDate tradeDate,
                                                       LocalDate expectedMarketDate) {
        DecisionTodayResp decision = context.getDecision();
        boolean decisionFresh = isDecisionFresh(decision, tradeDate, expectedMarketDate);
        String headline = buildHeadline(context, status, tradeDate, expectedMarketDate, decisionFresh);

        // 机会只来自当日可执行决策，不再把宽泛市场主题包装成用户机会。
        List<CommandDirectionItemResp> opportunityItems = new ArrayList<>();
        if (DashboardCommandStatusEnum.READY.equals(status)
                && decisionFresh
                && CollUtil.isNotEmpty(decision.getBuys())) {
            for (DecisionItemResp buyItem : decision.getBuys()) {
                if (Objects.isNull(buyItem) || !Boolean.TRUE.equals(buyItem.getExecutableHint())) {
                    continue;
                }
                String itemName = StringUtils.isNotBlank(buyItem.getName())
                        ? buyItem.getName().trim() : buyItem.getCode();
                if (StringUtils.isBlank(itemName)) {
                    continue;
                }
                opportunityItems.add(CommandDirectionItemResp.builder()
                        .name(itemName)
                        .reason(buildBuyAdvice(buyItem))
                        .build());
                if (opportunityItems.size() >= 2) {
                    break;
                }
            }
        }

        // 风险只展示用户持仓卖出项和新鲜观察触发，不重复市场广度、涨跌停等大盘信息。
        List<CommandDirectionItemResp> riskItems = new ArrayList<>();
        if (decisionFresh && CollUtil.isNotEmpty(decision.getSells())) {
            for (DecisionItemResp sellItem : decision.getSells()) {
                if (Objects.isNull(sellItem)) {
                    continue;
                }
                String itemName = StringUtils.isNotBlank(sellItem.getName())
                        ? sellItem.getName().trim() : sellItem.getCode();
                if (StringUtils.isBlank(itemName)) {
                    continue;
                }
                riskItems.add(CommandDirectionItemResp.builder()
                        .name(itemName)
                        .reason(buildSellAdvice(sellItem))
                        .build());
                if (riskItems.size() >= 2) {
                    break;
                }
            }
        }
        if (riskItems.size() < 2 && CollUtil.isNotEmpty(context.getObserveAlerts())) {
            for (ObservePoolResp observeAlert : context.getObserveAlerts()) {
                if (Objects.isNull(observeAlert)
                        || !"TRIGGERED".equals(observeAlert.getStatus())
                        || Objects.isNull(observeAlert.getTriggeredAt())
                        || observeAlert.getTriggeredAt().toLocalDate().isBefore(expectedMarketDate)) {
                    continue;
                }
                String itemName = StringUtils.isNotBlank(observeAlert.getName())
                        ? observeAlert.getName().trim() : observeAlert.getCode();
                if (StringUtils.isBlank(itemName)) {
                    continue;
                }
                String reason = "观察条件已触发，开盘前复核";
                if (StringUtils.isNotBlank(observeAlert.getStatusHint())) {
                    reason = observeAlert.getStatusHint().trim();
                } else if (StringUtils.isNotBlank(observeAlert.getTriggerLabel())) {
                    reason = observeAlert.getTriggerLabel().trim();
                } else if (StringUtils.isNotBlank(observeAlert.getReason())) {
                    reason = observeAlert.getReason().trim();
                }
                riskItems.add(CommandDirectionItemResp.builder()
                        .name(itemName)
                        .reason(reason)
                        .build());
                if (riskItems.size() >= 2) {
                    break;
                }
            }
        }

        List<CommandWatchConditionResp> watchConditions = new ArrayList<>();
        int executableCount = Objects.nonNull(decision)
                && Objects.nonNull(decision.getExecutableCount())
                ? Math.max(0, decision.getExecutableCount()) : 0;
        if (DashboardCommandStatusEnum.BLOCKED.equals(status)
                || DashboardCommandStatusEnum.STALE.equals(status)) {
            watchConditions.add(CommandWatchConditionResp.builder()
                    .title("恢复使用")
                    .condition("行情和晨报日期恢复正常，并重新生成 " + tradeDate + " 决策后再评估新仓")
                    .build());
        } else if (DashboardCommandStatusEnum.GENERATING.equals(status)) {
            watchConditions.add(CommandWatchConditionResp.builder()
                    .title("等待生成")
                    .condition("新一轮决策完成并发布前，不执行旧候选")
                    .build());
        } else if (DashboardCommandStatusEnum.PARTIAL.equals(status)) {
            watchConditions.add(CommandWatchConditionResp.builder()
                    .title("恢复执行")
                    .condition("补齐盘前数据并重新生成 " + tradeDate + " 决策后再下单")
                    .build());
        } else if (!decisionFresh) {
            watchConditions.add(CommandWatchConditionResp.builder()
                    .title("恢复新仓")
                    .condition("生成 " + tradeDate + " 决策，且数据至少覆盖 "
                            + expectedMarketDate + " 行情后，再处理可执行候选")
                    .build());
        } else if (executableCount > 0) {
            String condition = "候选失去可执行标记或开仓门禁失效";
            if (CollUtil.isNotEmpty(decision.getBuys())) {
                for (DecisionItemResp buyItem : decision.getBuys()) {
                    if (Objects.nonNull(buyItem)
                            && Boolean.TRUE.equals(buyItem.getExecutableHint())
                            && Objects.nonNull(buyItem.getStopLossPrice())) {
                        String itemName = StringUtils.isNotBlank(buyItem.getName())
                                ? buyItem.getName().trim() : buyItem.getCode();
                        condition = itemName + "跌破止损价"
                                + formatNumber(buyItem.getStopLossPrice()) + "，或开仓门禁失效";
                        break;
                    }
                }
            }
            watchConditions.add(CommandWatchConditionResp.builder()
                    .title("取消买入")
                    .condition(condition)
                    .build());
        }

        return PreMarketSummaryResp.builder()
                .headline(headline)
                .opportunityItems(opportunityItems)
                .riskItems(riskItems)
                .evidenceItems(List.of())
                .watchConditions(watchConditions)
                .build();
    }

    private String buildHeadline(DashboardCommandContextBO context,
                                 DashboardCommandStatusEnum status,
                                 LocalDate tradeDate,
                                 LocalDate expectedMarketDate,
                                 boolean decisionFresh) {
        if (DashboardCommandStatusEnum.BLOCKED.equals(status)) {
            MarketBriefingResp marketBriefing = context.getMarketBriefing();
            if (Objects.isNull(marketBriefing)) {
                return "市场简报缺失；当前没有可执行盘前结论，先完成收盘数据同步。";
            }
            if ("RED".equalsIgnoreCase(marketBriefing.getDataLevel())) {
                return "市场数据等级为 RED；当前清单不可执行，先修复缺失数据并重新生成决策。";
            }
            return "市场数据不完整；当前清单不可执行，补齐数据后重新生成目标交易日决策。";
        }
        if (DashboardCommandStatusEnum.STALE.equals(status)) {
            MarketBriefingResp marketBriefing = context.getMarketBriefing();
            if (Objects.nonNull(marketBriefing)
                    && Objects.nonNull(marketBriefing.getAsOf())
                    && marketBriefing.getAsOf().isBefore(expectedMarketDate)) {
                return "行情数据截至 " + marketBriefing.getAsOf() + "，未覆盖 "
                        + expectedMarketDate + "；刷新后重新生成 " + tradeDate + " 决策。";
            }
            MorningBriefingResp morningBriefing = context.getMorningBriefing();
            if (Objects.nonNull(morningBriefing) && Objects.nonNull(morningBriefing.getTradeDate())) {
                return "晨报对应 " + morningBriefing.getTradeDate() + "，与目标交易日 "
                        + tradeDate + " 不一致；刷新晨报后再生成决策。";
            }
            return "盘前数据日期不一致；当前清单不可执行，刷新后重新生成目标交易日决策。";
        }
        if (DashboardCommandStatusEnum.GENERATING.equals(status)) {
            return "新一轮决策正在生成；完成并发布前不沿用旧候选，只复核已有持仓风险。";
        }
        DecisionTodayResp decision = context.getDecision();
        if (!decisionFresh) {
            if (!isDecisionGeneratedForTradeDate(decision, tradeDate)) {
                return "目标交易日决策尚未生成；当前没有可执行买卖清单，先生成 "
                        + tradeDate + " 决策。";
            }
            if (Objects.isNull(decision.getDataAsOf())) {
                return "决策数据截止日缺失；当前清单不可执行，重新生成 " + tradeDate + " 决策。";
            }
            return "决策基于 " + decision.getDataAsOf() + " 行情，未覆盖 " + expectedMarketDate
                    + "；当前清单不可执行，重新生成 " + tradeDate + " 决策。";
        }
        int sellCount = Objects.nonNull(decision.getSellCount())
                ? Math.max(0, decision.getSellCount()) : 0;
        int executableCount = Objects.nonNull(decision.getExecutableCount())
                ? Math.max(0, decision.getExecutableCount()) : 0;
        if (sellCount > 0 && executableCount > 0) {
            DecisionItemResp firstSell = firstDecisionItem(decision.getSells(), false);
            DecisionItemResp firstBuy = firstDecisionItem(decision.getBuys(), true);
            if (Objects.nonNull(firstSell) && Objects.nonNull(firstBuy)) {
                return "先" + buildNamedSellAction(firstSell)
                        + "；新仓只做" + decisionItemName(firstBuy) + "。";
            }
            return "先处理" + sellCount + "项卖出/减仓；再执行"
                    + executableCount + "个新仓。";
        }
        if (sellCount > 0) {
            String sellNames = joinDecisionItemNames(decision.getSells(), false, sellCount);
            if (StringUtils.isNotBlank(sellNames)) {
                return "先处理" + sellNames + "；今天不开新仓。";
            }
            return "先处理" + sellCount + "项卖出/减仓；今天不开新仓。";
        }
        if (executableCount > 0) {
            String buyNames = joinDecisionItemNames(decision.getBuys(), true, executableCount);
            if (StringUtils.isNotBlank(buyNames)) {
                return "新仓只做" + buyNames + "，按建议仓位并严格止损。";
            }
            return "执行" + executableCount + "个新仓候选，按建议仓位和止损价下单。";
        }
        return "今天无买卖动作，保持现仓。";
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

        DecisionTodayResp decision = context.getDecision();
        int freshTriggeredCount = countFreshTriggeredAlerts(context.getObserveAlerts(), expectedMarketDate);
        OperationGuideItemResp riskItem = buildRiskItem(decision, tradeDate,
                expectedMarketDate, freshTriggeredCount);
        OperationGuideItemResp buyItem = buildBuyItem(decision, tradeDate,
                expectedMarketDate, phase, status);
        List<OperationGuideItemResp> items = new ArrayList<>();
        if (DashboardCommandStatusEnum.BLOCKED.equals(status)
                || DashboardCommandStatusEnum.STALE.equals(status)) {
            items.add(buildInvalidationItem());
        } else if (DashboardCommandStatusEnum.GENERATING.equals(status)
                || DashboardCommandStatusEnum.PARTIAL.equals(status)
                || !isDecisionFresh(decision, tradeDate, expectedMarketDate)) {
            items.add(buyItem);
        } else {
            if (OperationGuideStatusEnum.REQUIRED.getCode().equals(riskItem.getStatus())) {
                items.add(riskItem);
            }
            if (OperationGuideStatusEnum.READY.getCode().equals(buyItem.getStatus())) {
                items.add(buyItem);
            }
        }
        for (int index = 0; index < items.size(); index++) {
            items.get(index).setPriority(index + 1);
        }

        String blockedReason = null;
        if (DashboardCommandStatusEnum.BLOCKED.equals(status)) {
            blockedReason = "市场数据缺失或等级为 RED，请先补齐数据";
        } else if (DashboardCommandStatusEnum.STALE.equals(status)) {
            blockedReason = "市场或晨报日期已过期，请先刷新数据";
        }
        String summary;
        if (DashboardCommandStatusEnum.BLOCKED.equals(status)) {
            summary = "市场数据不可用，新增仓保持禁用；补齐数据后重新生成目标交易日决策。";
        } else if (DashboardCommandStatusEnum.STALE.equals(status)) {
            summary = "市场或晨报日期已过期，新增仓保持禁用；刷新后重新生成目标交易日决策。";
        } else if (DashboardCommandStatusEnum.GENERATING.equals(status)) {
            summary = "新一轮决策正在生成，完成并发布前不执行旧候选。";
        } else if (DashboardCommandStatusEnum.PARTIAL.equals(status)) {
            summary = "盘前数据不完整，补齐并重算决策后再下单。";
        } else if (OperationGuideStatusEnum.REQUIRED.getCode().equals(riskItem.getStatus())
                && OperationGuideStatusEnum.READY.getCode().equals(buyItem.getStatus())) {
            summary = "先处理" + buildRiskSummary(decision, freshTriggeredCount) + "，再执行"
                    + buyItem.getTargetCount() + "个新仓。";
        } else if (OperationGuideStatusEnum.REQUIRED.getCode().equals(riskItem.getStatus())) {
            summary = "先处理" + buildRiskSummary(decision, freshTriggeredCount)
                    + "；今天不开新仓。";
        } else if (OperationGuideStatusEnum.READY.getCode().equals(buyItem.getStatus())) {
            summary = "执行" + buyItem.getTargetCount() + "个新仓候选，按建议仓位和止损价下单。";
        } else if (!isDecisionGeneratedForTradeDate(decision, tradeDate)) {
            summary = "生成" + tradeDate + "决策后再下单。";
        } else if (!isDecisionFresh(decision, tradeDate, expectedMarketDate)) {
            summary = Objects.isNull(decision.getDataAsOf())
                    ? "决策数据截止日缺失，重新生成前不执行当前清单。"
                    : "决策基于 " + decision.getDataAsOf() + " 行情，未覆盖 "
                    + expectedMarketDate + "；重新生成前不执行当前清单。";
        } else {
            summary = "今天无买卖动作，保持现仓。";
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
        boolean decisionGenerated = isDecisionGeneratedForTradeDate(decision, tradeDate);
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
        } else if (!decisionGenerated) {
            status = OperationGuideStatusEnum.WAIT;
            conditionText = "目标交易日决策尚未生成，暂时无法确认持仓卖出项";
        } else if (!decisionFresh) {
            status = OperationGuideStatusEnum.WAIT;
            conditionText = "决策数据未覆盖最新行情，重新生成后确认持仓卖出项";
        } else {
            status = OperationGuideStatusEnum.DONE;
            conditionText = "当前无卖出项或新鲜触发提醒";
        }
        String actionText = "等待新决策确认持仓风险";
        if (OperationGuideStatusEnum.REQUIRED.equals(status)) {
            List<String> actions = new ArrayList<>();
            List<String> conditions = new ArrayList<>();
            if (decisionFresh && CollUtil.isNotEmpty(decision.getSells())) {
                for (DecisionItemResp sellItem : decision.getSells()) {
                    if (Objects.isNull(sellItem)) {
                        continue;
                    }
                    String itemName = decisionItemName(sellItem);
                    if (StringUtils.isBlank(itemName)) {
                        continue;
                    }
                    if (actions.size() < 2) {
                        actions.add(buildNamedSellAction(sellItem));
                    }
                    if (conditions.size() < 2 && StringUtils.isNotBlank(sellItem.getExitRule())) {
                        conditions.add(itemName + "：" + sellItem.getExitRule().trim());
                    }
                }
            }
            if (CollUtil.isNotEmpty(actions)) {
                actionText = String.join("；", actions);
                if (sellCount > actions.size()) {
                    actionText += "；共" + sellCount + "项";
                }
            } else if (sellCount > 0) {
                actionText = "执行" + sellCount + "项卖出/减仓";
            } else {
                actionText = "复核" + freshTriggeredCount + "个已触发观察项";
            }
            if (CollUtil.isNotEmpty(conditions)) {
                conditionText = String.join("；", conditions);
            }
            if (freshTriggeredCount > 0 && sellCount > 0) {
                actionText += "；复核" + freshTriggeredCount + "个已触发观察项";
            }
        } else if (OperationGuideStatusEnum.DONE.equals(status)) {
            actionText = "当前无优先风险动作";
        }
        return OperationGuideItemResp.builder()
                .priority(1)
                .code(OperationGuideCodeEnum.RISK_FIRST.getCode())
                .status(status.getCode())
                .title(sellCount > 0 ? "卖出/减仓" : "观察提醒")
                .actionText(actionText)
                .conditionText(conditionText)
                .targetCount(targetCount)
                .targetType(OperationTargetTypeEnum.POSITION.getCode())
                .build();
    }

    private String buildRiskSummary(DecisionTodayResp decision, int freshTriggeredCount) {
        int sellCount = Objects.nonNull(decision) && Objects.nonNull(decision.getSellCount())
                ? Math.max(0, decision.getSellCount()) : 0;
        if (sellCount > 0 && freshTriggeredCount > 0) {
            return sellCount + "项卖出/减仓和" + freshTriggeredCount + "个观察提醒";
        }
        if (sellCount > 0) {
            return sellCount + "项卖出/减仓";
        }
        return freshTriggeredCount + "个观察提醒";
    }

    private OperationGuideItemResp buildBuyItem(DecisionTodayResp decision,
                                                LocalDate tradeDate,
                                                LocalDate expectedMarketDate,
                                                DashboardCommandPhaseEnum phase,
                                                DashboardCommandStatusEnum commandStatus) {
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
        } else if (DashboardCommandStatusEnum.PARTIAL.equals(commandStatus)) {
            status = OperationGuideStatusEnum.WAIT;
            conditionText = "盘前数据不完整，补齐并重新生成决策后再下单";
        } else if (DashboardCommandPhaseEnum.NON_TRADING_DAY.equals(phase)) {
            status = OperationGuideStatusEnum.WAIT;
            conditionText = "当前为非交易日，仅准备下个交易日计划";
        } else if (!isDecisionGeneratedForTradeDate(decision, tradeDate)) {
            status = OperationGuideStatusEnum.WAIT;
            conditionText = "目标交易日决策尚未生成";
        } else if (!isDecisionFresh(decision, tradeDate, expectedMarketDate)) {
            status = OperationGuideStatusEnum.WAIT;
            conditionText = Objects.isNull(decision.getDataAsOf())
                    ? "决策数据截止日缺失，等待重新生成"
                    : "决策仅覆盖 " + decision.getDataAsOf() + " 行情，等待重新生成";
        } else if (executableCount > 0) {
            status = OperationGuideStatusEnum.READY;
            conditionText = "仅处理仍标记为可执行的候选";
        } else {
            status = OperationGuideStatusEnum.WAIT;
            conditionText = "当前没有可执行候选，不将普通买入候选视为可执行";
        }
        String actionText = DashboardCommandStatusEnum.GENERATING.equals(commandStatus)
                ? "等待本轮决策发布" : "重新生成" + tradeDate + "决策";
        if (OperationGuideStatusEnum.READY.equals(status)) {
            DecisionItemResp executableBuy = null;
            if (CollUtil.isNotEmpty(decision.getBuys())) {
                for (DecisionItemResp buyItem : decision.getBuys()) {
                    if (Objects.nonNull(buyItem) && Boolean.TRUE.equals(buyItem.getExecutableHint())) {
                        executableBuy = buyItem;
                        break;
                    }
                }
            }
            if (Objects.nonNull(executableBuy)) {
                String itemName = decisionItemName(executableBuy);
                actionText = "买" + itemName;
                if (Objects.nonNull(executableBuy.getSuggestedWeight())) {
                    actionText += "至" + formatWeight(executableBuy.getSuggestedWeight()) + "仓位";
                }
                String priceAdvice = buildBuyPriceAdvice(executableBuy);
                if (StringUtils.isNotBlank(priceAdvice)) {
                    conditionText = priceAdvice;
                }
            } else {
                actionText = "执行" + executableCount + "个可买候选";
            }
        }
        return OperationGuideItemResp.builder()
                .priority(2)
                .code(OperationGuideCodeEnum.BUY_CONDITIONALLY.getCode())
                .status(status.getCode())
                .title(OperationGuideStatusEnum.READY.equals(status) ? "新仓计划" : "重算决策")
                .actionText(actionText)
                .conditionText(conditionText)
                .targetCount(executableCount)
                .targetType(OperationTargetTypeEnum.DECISION.getCode())
                .build();
    }

    private String buildBuyAdvice(DecisionItemResp buyItem) {
        List<String> adviceParts = new ArrayList<>();
        if (Objects.nonNull(buyItem.getSuggestedWeight())) {
            adviceParts.add("买至" + formatWeight(buyItem.getSuggestedWeight()) + "仓位");
        }
        String priceAdvice = buildBuyPriceAdvice(buyItem);
        if (StringUtils.isNotBlank(priceAdvice)) {
            adviceParts.add(priceAdvice);
        }
        if (CollUtil.isEmpty(adviceParts) && StringUtils.isNotBlank(buyItem.getReason())) {
            adviceParts.add(buyItem.getReason().trim());
        }
        return CollUtil.isNotEmpty(adviceParts)
                ? String.join("；", adviceParts) : "缺少仓位和价格，不下单";
    }

    private String buildBuyPriceAdvice(DecisionItemResp buyItem) {
        List<String> priceParts = new ArrayList<>();
        if (Objects.nonNull(buyItem.getReferencePrice())) {
            priceParts.add("参考" + formatNumber(buyItem.getReferencePrice()));
        }
        if (Objects.nonNull(buyItem.getStopLossPrice())) {
            priceParts.add("止损" + formatNumber(buyItem.getStopLossPrice()));
        }
        if (Objects.nonNull(buyItem.getTakeProfitPrice())) {
            priceParts.add("止盈" + formatNumber(buyItem.getTakeProfitPrice()));
        }
        return String.join("；", priceParts);
    }

    private String buildSellAdvice(DecisionItemResp sellItem) {
        List<String> adviceParts = new ArrayList<>();
        adviceParts.add(buildSellAction(sellItem));
        if (StringUtils.isNotBlank(sellItem.getExitRule())) {
            adviceParts.add(sellItem.getExitRule().trim());
        } else {
            if (Objects.nonNull(sellItem.getStopLossPrice())) {
                adviceParts.add("止损" + formatNumber(sellItem.getStopLossPrice()));
            }
            if (Objects.nonNull(sellItem.getTakeProfitPrice())) {
                adviceParts.add("止盈" + formatNumber(sellItem.getTakeProfitPrice()));
            }
        }
        return String.join("；", adviceParts);
    }

    private String buildSellAction(DecisionItemResp sellItem) {
        if ("SELL".equalsIgnoreCase(sellItem.getAction())) {
            return "清仓";
        }
        if (Objects.nonNull(sellItem.getSuggestedWeight())) {
            return "减至" + formatWeight(sellItem.getSuggestedWeight()) + "仓位";
        }
        return "减仓";
    }

    private String buildNamedSellAction(DecisionItemResp sellItem) {
        String itemName = decisionItemName(sellItem);
        if ("SELL".equalsIgnoreCase(sellItem.getAction())) {
            return "清仓" + itemName;
        }
        if (Objects.nonNull(sellItem.getSuggestedWeight())) {
            return itemName + buildSellAction(sellItem);
        }
        return "减仓" + itemName;
    }

    private DecisionItemResp firstDecisionItem(List<DecisionItemResp> items, boolean executableOnly) {
        if (CollUtil.isEmpty(items)) {
            return null;
        }
        for (DecisionItemResp item : items) {
            if (Objects.isNull(item)
                    || (executableOnly && !Boolean.TRUE.equals(item.getExecutableHint()))) {
                continue;
            }
            if (StringUtils.isNotBlank(decisionItemName(item))) {
                return item;
            }
        }
        return null;
    }

    private String joinDecisionItemNames(List<DecisionItemResp> items,
                                         boolean executableOnly,
                                         int totalCount) {
        if (CollUtil.isEmpty(items)) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (DecisionItemResp item : items) {
            if (Objects.isNull(item)
                    || (executableOnly && !Boolean.TRUE.equals(item.getExecutableHint()))) {
                continue;
            }
            String itemName = decisionItemName(item);
            if (StringUtils.isBlank(itemName)) {
                continue;
            }
            names.add(itemName);
            if (names.size() >= 2) {
                break;
            }
        }
        if (CollUtil.isEmpty(names)) {
            return null;
        }
        String nameText = String.join("、", names);
        return totalCount > names.size() ? nameText + "等" + totalCount + "项" : nameText;
    }

    private String decisionItemName(DecisionItemResp item) {
        return StringUtils.isNotBlank(item.getName()) ? item.getName().trim() : item.getCode();
    }

    private String formatWeight(BigDecimal weight) {
        return formatNumber(weight.multiply(new BigDecimal("100"))) + "%";
    }

    private String formatNumber(BigDecimal number) {
        return number.stripTrailingZeros().toPlainString();
    }

    private OperationGuideItemResp buildInvalidationItem() {
        return OperationGuideItemResp.builder()
                .priority(3)
                .code(OperationGuideCodeEnum.REFRESH_DATA.getCode())
                .status(OperationGuideStatusEnum.BLOCKED.getCode())
                .title("刷新数据")
                .actionText("停止新增动作并刷新数据")
                .conditionText("行情和晨报日期恢复正常并重新生成目标交易日决策后，才可恢复新仓")
                .targetCount(0)
                .targetType(OperationTargetTypeEnum.DATA.getCode())
                .build();
    }

    private boolean isDecisionFresh(DecisionTodayResp decision,
                                    LocalDate tradeDate,
                                    LocalDate expectedMarketDate) {
        return isDecisionGeneratedForTradeDate(decision, tradeDate)
                && Objects.nonNull(decision.getDataAsOf())
                && !decision.getDataAsOf().isBefore(expectedMarketDate);
    }

    private boolean isDecisionGeneratedForTradeDate(DecisionTodayResp decision, LocalDate tradeDate) {
        return Objects.nonNull(decision)
                && Objects.nonNull(decision.getActionDate())
                && tradeDate.equals(decision.getActionDate())
                && Boolean.TRUE.equals(decision.getGenerated());
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
