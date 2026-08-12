package com.awe.apex.quant.job;

import com.awe.apex.quant.domain.dto.SyncStartReq;
import com.awe.apex.quant.market.TradingCalendar;
import com.awe.apex.quant.service.IDataSyncJobService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 智能决策自动调度。
 */
@Slf4j
@Component
public class DecisionScheduler {

    @Resource
    private IDataSyncJobService dataSyncJobService;

    /**
     * 午间和收盘后自动生成决策。
     */
    @Scheduled(cron = "0 40 11,15 * * MON-FRI", zone = "Asia/Shanghai")
    public void runMainSessions() {
        runScheduledDecision(LocalDate.now());
    }

    /**
     * 16:10 使用更完整的收盘数据再次生成决策。
     */
    @Scheduled(cron = "0 10 16 * * MON-FRI", zone = "Asia/Shanghai")
    public void runClosingSession() {
        runScheduledDecision(LocalDate.now());
    }

    void runScheduledDecision(LocalDate actionDate) {
        if (!TradingCalendar.isTradingDay(actionDate)) {
            log.info("智能决策定时任务跳过：非交易日 actionDate={}", actionDate);
            return;
        }
        try {
            SyncStartReq request = new SyncStartReq();
            request.setTaskType("DECISION");
            dataSyncJobService.start(request);
            log.info("智能决策定时任务已提交 actionDate={}", actionDate);
        } catch (Exception ex) {
            log.warn("智能决策定时任务提交跳过 actionDate={} reason={}", actionDate, ex.getMessage());
        }
    }
}
