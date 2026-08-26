package com.awe.apex.quant.job;

import com.awe.apex.quant.bot.config.ApexBotProperties;
import com.awe.apex.quant.bot.service.IBotNotificationService;
import com.awe.apex.quant.domain.dto.DailyPreMarketReportResp;
import com.awe.apex.quant.domain.dto.MorningBriefingResp;
import com.awe.apex.quant.domain.dto.NewsRefreshResp;
import com.awe.apex.quant.service.IDailyPreMarketReportService;
import com.awe.apex.quant.service.IMorningBriefingService;
import com.awe.apex.quant.service.IMarketOpinionService;
import com.awe.apex.quant.service.INewsService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 每日盘前晨报任务。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "apex.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MorningBriefingScheduler {

    @Resource
    private ApexBotProperties properties;

    @Resource
    private INewsService newsService;

    @Resource
    private IMorningBriefingService morningBriefingService;

    @Resource
    private IDailyPreMarketReportService dailyPreMarketReportService;

    @Resource
    private IMarketOpinionService marketOpinionService;

    @Resource
    private IBotNotificationService notificationService;

    /**
     * 上海时间 06:35 更新夜间新闻并推送晨报，为 07:30 截止预留 55 分钟。
     */
    @Scheduled(cron = "0 35 6 * * *", zone = "Asia/Shanghai")
    public void generateMorningBriefing() {
        LocalDateTime startedAt = LocalDateTime.now();
        try {
            // 1. 先同步可追溯观点，再生成首页观点共识与分歧
            try {
                marketOpinionService.refresh();
            } catch (Exception ex) {
                log.warn("盘前晨报市场观点刷新失败，继续使用本地快照，原因={}", ex.getMessage());
            }

            // 2. 刷新夜间资讯，保证消息面摘要包含最新内容
            try {
                NewsRefreshResp refreshResp = newsService.refresh("eastmoney,cls,ths,sina", 80);
                log.info("盘前晨报新闻刷新完成，结果={}", refreshResp.getMessage());
            } catch (Exception ex) {
                log.warn("盘前晨报新闻刷新失败，继续使用本地新闻，原因={}", ex.getMessage());
            }

            // 3. 始终生成全局晨报；存在 Bot 绑定用户时继续生成包含其组合的完整研报
            MorningBriefingResp briefing = morningBriefingService.generate();
            DailyPreMarketReportResp dailyReport = null;
            if (Objects.nonNull(properties.getApexUserId())) {
                try {
                    dailyReport = dailyPreMarketReportService.generateForUser(properties.getApexUserId());
                } catch (Exception ex) {
                    log.warn("完整盘前研报生成失败，用户编号={}，继续使用基础晨报，原因={}",
                            properties.getApexUserId(), ex.getMessage());
                }
            }
            if (properties.getMorningBriefing().isEnabled()) {
                if (Objects.nonNull(dailyReport)) {
                    notificationService.notifyDailyPreMarketReport(dailyReport);
                } else {
                    notificationService.notifyMorningBriefing(briefing);
                }
            }
            long durationSeconds = Duration.between(startedAt, LocalDateTime.now()).toSeconds();
            log.info("盘前晨报完成，数据等级={}，行情数量={}，新闻数量={}，耗时秒={}",
                    briefing.getDataLevel(), briefing.getMarketQuotes().size(), briefing.getNewsTitles().size(), durationSeconds);
        } catch (Exception ex) {
            log.error("盘前晨报失败，开始时间={}，原因={}", startedAt, ex.getMessage(), ex);
        }
    }
}
