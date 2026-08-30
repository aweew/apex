package com.awe.apex.quant.job;

import com.awe.apex.quant.domain.dto.WeekendMarketReportResp;
import com.awe.apex.quant.service.IWeekendMarketReportService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 每周日晚上生成全市场周末消息面专题研报。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "apex.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WeekendMarketReportScheduler {

    @Resource
    private IWeekendMarketReportService weekendMarketReportService;

    /**
     * 上海时间每周日 21:00 生成周末研报。
     */
    @Scheduled(cron = "0 0 21 * * SUN", zone = "Asia/Shanghai")
    public void generateWeekendMarketReport() {
        LocalDateTime startedAt = LocalDateTime.now();
        try {
            WeekendMarketReportResp report = weekendMarketReportService.refresh();
            long durationSeconds = Duration.between(startedAt, LocalDateTime.now()).toSeconds();
            log.info("周末研报定时生成完成，报告日期={}，最后交易日={}，来源={}，数据等级={}，新闻数={}，观点数={}，耗时秒={}",
                    report.getReportDate(), report.getLastTradeDate(), report.getReportSource(), report.getDataLevel(),
                    report.getWeekendNews().size(), report.getMarketOpinions().size(), durationSeconds);
        } catch (Exception ex) {
            log.error("周末研报定时生成失败，开始时间={}，原因={}", startedAt, ex.getMessage(), ex);
        }
    }
}
