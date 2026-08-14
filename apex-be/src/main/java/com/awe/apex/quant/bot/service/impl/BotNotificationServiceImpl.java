package com.awe.apex.quant.bot.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.bot.client.WeClawMessageClient;
import com.awe.apex.quant.bot.config.ApexBotProperties;
import com.awe.apex.quant.bot.service.IBotNotificationService;
import com.awe.apex.quant.domain.dto.BotHoldingRiskItem;
import com.awe.apex.quant.domain.dto.BotHoldingRiskResp;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import com.awe.apex.quant.domain.dto.MorningBriefingResp;
import com.awe.apex.quant.domain.dto.ObservePoolResp;
import com.awe.apex.quant.domain.dto.WatchlistMoverResp;
import com.awe.apex.quant.domain.dto.WatchlistResp;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ClawBot 主动通知服务实现。
 */
@Service
public class BotNotificationServiceImpl implements IBotNotificationService {

    @Resource
    private ApexBotProperties properties;

    @Resource
    private WeClawMessageClient messageClient;

    private final Map<String, Long> notificationExpireAt = new ConcurrentHashMap<>();

    /**
     * 在决策任务成功后推送决策摘要。
     *
     * @param decision 决策结果
     */
    @Override
    public void notifyDecision(DecisionTodayResp decision) {
        if (Objects.isNull(decision) || StringUtils.isBlank(decision.getRunNo())) {
            return;
        }
        String eventKey = "DECISION:" + decision.getRunNo();
        StringBuilder message = new StringBuilder("[Apex 智能决策]\n");
        message.append("买入 ").append(defaultInteger(decision.getBuyCount()))
                .append("，卖出 ").append(defaultInteger(decision.getSellCount()))
                .append("，持有 ").append(defaultInteger(decision.getHoldCount())).append("\n");
        if (StringUtils.isNotBlank(decision.getRiskNote())) {
            message.append("风险：").append(decision.getRiskNote()).append("\n");
        }
        if (StringUtils.isNotBlank(decision.getMessage())) {
            message.append(decision.getMessage()).append("\n");
        }
        if (Objects.nonNull(decision.getAsOfTime())) {
            message.append("数据时间：").append(decision.getAsOfTime()).append("\n");
        }
        message.append("仅供研究，不构成投资建议。");
        sendOnce(eventKey, message.toString());
    }

    /**
     * 推送盘前晨报。
     *
     * @param briefing 盘前晨报
     */
    @Override
    public void notifyMorningBriefing(MorningBriefingResp briefing) {
        if (Objects.isNull(briefing) || StringUtils.isBlank(briefing.getSummary())) {
            return;
        }
        String reportDate = Objects.nonNull(briefing.getGeneratedAt())
                ? briefing.getGeneratedAt().toLocalDate().toString() : "unknown";
        StringBuilder message = new StringBuilder("[Apex 盘前晨报]\n");
        message.append(briefing.getSummary()).append("\n");
        if (CollUtil.isNotEmpty(briefing.getNewsTitles())) {
            message.append("重点：").append(String.join("；", briefing.getNewsTitles())).append("\n");
        }
        if (StringUtils.isNotBlank(briefing.getDataLevel()) && !"GREEN".equals(briefing.getDataLevel())) {
            message.append("数据完整性：").append(briefing.getDataLevel()).append("\n");
        }
        sendOnce("MORNING_BRIEFING:" + reportDate, message.toString());
    }

    /**
     * 合并推送自选、观察池和持仓风控告警。
     *
     * @param movers       自选异动
     * @param observeItems 观察池告警
     * @param holdingRisk  真实持仓风控告警
     */
    @Override
    public void notifyMarketAlerts(WatchlistMoverResp movers,
                                   List<ObservePoolResp> observeItems,
                                   BotHoldingRiskResp holdingRisk) {
        long now = Instant.now().getEpochSecond();
        StringBuilder message = new StringBuilder("[Apex 盘中提醒]\n");
        List<String> eventKeys = new ArrayList<>();
        int eventCount = 0;

        if (Objects.nonNull(movers)) {
            eventCount += appendMovers(message, eventKeys, "大涨", movers.getGainers(), now);
            eventCount += appendMovers(message, eventKeys, "大跌", movers.getLosers(), now);
        }
        if (CollUtil.isNotEmpty(observeItems)) {
            int observeCount = 0;
            for (ObservePoolResp item : observeItems) {
                String itemEventKey = "OBSERVE:" + item.getId() + ":" + item.getStatus();
                if (isCoolingDown(itemEventKey, now)) {
                    continue;
                }
                message.append("观察：").append(defaultText(item.getName(), item.getCode()))
                        .append(" ").append(defaultText(item.getStatus(), "READY"));
                if (Objects.nonNull(item.getLatestPrice())) {
                    message.append("，现价 ").append(item.getLatestPrice());
                }
                message.append("\n");
                eventKeys.add(itemEventKey);
                eventCount++;
                observeCount++;
                if (observeCount >= 5) {
                    break;
                }
            }
        }
        if (Objects.nonNull(holdingRisk) && CollUtil.isNotEmpty(holdingRisk.getAlerts())) {
            int riskCount = 0;
            for (BotHoldingRiskItem alert : holdingRisk.getAlerts()) {
                if (!"CRITICAL".equals(alert.getLevel()) && !"WARN".equals(alert.getLevel())) {
                    continue;
                }
                String itemEventKey = "RISK:" + alert.getCode() + ":"
                        + defaultText(alert.getRiskType(), "OTHER") + ":" + alert.getLevel();
                if (isCoolingDown(itemEventKey, now)) {
                    continue;
                }
                message.append("风控：[").append(alert.getLevel()).append("] ")
                        .append(defaultText(alert.getName(), alert.getCode())).append("，")
                        .append(alert.getMessage()).append("\n");
                eventKeys.add(itemEventKey);
                eventCount++;
                riskCount++;
                if (riskCount >= 5) {
                    break;
                }
            }
        }
        if (eventCount == 0) {
            return;
        }
        message.append("请在 Apex 查看完整依据。仅供研究，不构成投资建议。");
        sendEvents(eventKeys, message.toString(), now);
    }

    private int appendMovers(StringBuilder message,
                             List<String> eventKeys,
                             String label,
                             List<WatchlistResp> stocks,
                             long now) {
        if (CollUtil.isEmpty(stocks)) {
            return 0;
        }
        int eventCount = 0;
        for (WatchlistResp stock : stocks) {
            String itemEventKey = "MOVER:" + stock.getCode() + ":" + label;
            if (isCoolingDown(itemEventKey, now)) {
                continue;
            }
            message.append(label).append("：").append(defaultText(stock.getName(), stock.getCode()))
                    .append(" ").append(stock.getPctChg()).append("%")
                    .append("，现价 ").append(stock.getLatestPrice()).append("\n");
            eventKeys.add(itemEventKey);
            eventCount++;
            if (eventCount >= 5) {
                break;
            }
        }
        return eventCount;
    }

    private synchronized void sendOnce(String eventKey, String message) {
        long now = Instant.now().getEpochSecond();
        if (isCoolingDown(eventKey, now)) {
            return;
        }
        if (messageClient.sendText(message)) {
            notificationExpireAt.put(eventKey, now + cooldownSeconds());
        }
    }

    private synchronized void sendEvents(List<String> eventKeys, String message, long now) {
        if (CollUtil.isEmpty(eventKeys) || !messageClient.sendText(message)) {
            return;
        }
        long expireAt = now + cooldownSeconds();
        for (String eventKey : eventKeys) {
            notificationExpireAt.put(eventKey, expireAt);
        }
    }

    private boolean isCoolingDown(String eventKey, long now) {
        notificationExpireAt.entrySet().removeIf(entry -> entry.getValue() < now);
        Long expireAt = notificationExpireAt.get(eventKey);
        return Objects.nonNull(expireAt) && expireAt >= now;
    }

    private int cooldownSeconds() {
        return Math.max(60, properties.getNotificationCooldownSeconds());
    }

    private int defaultInteger(Integer value) {
        return Objects.nonNull(value) ? value : 0;
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.isNotBlank(value) ? value : defaultValue;
    }
}
