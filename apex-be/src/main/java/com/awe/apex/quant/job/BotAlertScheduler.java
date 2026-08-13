package com.awe.apex.quant.job;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.quant.bot.config.ApexBotProperties;
import com.awe.apex.quant.bot.service.IBotHoldingRiskService;
import com.awe.apex.quant.bot.service.IBotNotificationService;
import com.awe.apex.quant.domain.dto.BotHoldingRiskResp;
import com.awe.apex.quant.domain.dto.ObservePoolResp;
import com.awe.apex.quant.domain.dto.WatchlistMoverResp;
import com.awe.apex.quant.market.TradingCalendar;
import com.awe.apex.quant.service.IMyHoldingService;
import com.awe.apex.quant.service.IObservePoolService;
import com.awe.apex.quant.service.IWatchlistService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * ClawBot 盘中告警扫描任务。
 */
@Slf4j
@Component
public class BotAlertScheduler {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    private Clock clock = Clock.system(SHANGHAI_ZONE);

    @Resource
    private ApexBotProperties properties;

    @Resource
    private IWatchlistService watchlistService;

    @Resource
    private IMyHoldingService myHoldingService;

    @Resource
    private IObservePoolService observePoolService;

    @Resource
    private IBotHoldingRiskService botHoldingRiskService;

    @Resource
    private IBotNotificationService notificationService;

    /**
     * 交易时段每三分钟扫描自选、观察池和持仓风险。
     */
    @Scheduled(cron = "0 */3 9-11,13-14 * * MON-FRI", zone = "Asia/Shanghai")
    public void scanMarketAlerts() {
        if (!properties.getWeclaw().isEnabled()) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(SHANGHAI_ZONE);
        LocalDate today = now.toLocalDate();
        LocalTime time = now.toLocalTime();
        if (!TradingCalendar.isTradingDay(today) || !isTradingSession(time)) {
            return;
        }

        String group = properties.getWatchlistGroup();
        try {
            // 1. 刷新自选和持仓的轻量行情快照
            watchlistService.refreshQuotes(group, 20, false);
            List<String> holdingCodes = myHoldingService.listHoldingCodes();
            if (CollUtil.isNotEmpty(holdingCodes)) {
                myHoldingService.refreshQuotesForCodes(holdingCodes, false);
            }

            // 2. 基于同一轮最新快照计算三类告警
            WatchlistMoverResp movers = watchlistService.movers(
                    group, BigDecimal.valueOf(properties.getMoverThreshold()), 8);
            List<ObservePoolResp> observeItems = observePoolService.listReadyAlerts(8);
            BotHoldingRiskResp holdingRisk = botHoldingRiskService.analyze();

            // 3. 合并推送并在通知服务中做冷却去重
            notificationService.notifyMarketAlerts(movers, observeItems, holdingRisk);
        } catch (Exception ex) {
            log.warn("ClawBot 盘中告警扫描失败 group={} reason={}", group, ex.getMessage());
        }
    }

    private boolean isTradingSession(LocalTime time) {
        boolean morning = !time.isBefore(LocalTime.of(9, 30)) && !time.isAfter(LocalTime.of(11, 30));
        boolean afternoon = !time.isBefore(LocalTime.of(13, 0)) && !time.isAfter(LocalTime.of(15, 0));
        return morning || afternoon;
    }
}
