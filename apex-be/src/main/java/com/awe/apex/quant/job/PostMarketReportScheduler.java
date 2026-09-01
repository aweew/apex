package com.awe.apex.quant.job;

import com.awe.apex.quant.domain.dto.PostMarketReportResp;
import com.awe.apex.quant.market.TradingCalendar;
import com.awe.apex.quant.service.IPostMarketReportService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 每个交易日收盘后生成最新盘后总结。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "apex.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PostMarketReportScheduler {

    @Resource
    private IPostMarketReportService postMarketReportService;

    /**
     * 上海时间工作日 18:30 生成最新盘后总结。
     */
    @Scheduled(cron = "0 30 18 * * MON-FRI", zone = "Asia/Shanghai")
    public void generatePostMarketReport() {
        generatePostMarketReport(LocalDate.now());
    }

    void generatePostMarketReport(LocalDate currentDate) {
        if (!TradingCalendar.isTradingDay(currentDate)) {
            log.info("盘后总结定时生成跳过：非交易日，日期={}", currentDate);
            return;
        }
        LocalDateTime startedAt = LocalDateTime.now();
        try {
            PostMarketReportResp report = postMarketReportService.refresh();
            if (Objects.isNull(report)) {
                log.info("盘后总结定时生成跳过，开始时间={}", startedAt);
                return;
            }
            long durationSeconds = Duration.between(startedAt, LocalDateTime.now()).toSeconds();
            log.info("盘后总结定时生成完成，交易日={}，来源={}，数据等级={}，耗时秒={}",
                    report.getTradeDate(), report.getReportSource(), report.getDataLevel(), durationSeconds);
        } catch (Exception ex) {
            log.error("盘后总结定时生成失败，开始时间={}，原因={}", startedAt, ex.getMessage(), ex);
        }
    }
}
