package com.awe.apex.quant.job;

import com.awe.apex.quant.domain.dto.SyncStartReq;
import com.awe.apex.quant.market.TradingCalendar;
import com.awe.apex.quant.service.IDataSyncJobService;
import com.awe.apex.quant.service.IDecisionOutcomeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 智能决策自动调度。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "apex.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DecisionScheduler {

    @Resource
    private IDataSyncJobService dataSyncJobService;

    @Resource
    private IDecisionOutcomeService decisionOutcomeService;

    /**
     * 06:50 使用盘前已就绪数据为启用用户生成决策。
     */
    @Scheduled(cron = "0 50 6 * * MON-FRI", zone = "Asia/Shanghai")
    public void runPreMarketSession() {
        runScheduledDecision(LocalDate.now());
    }

    /**
     * 午间和收盘后自动生成决策。
     */
    @Scheduled(cron = "0 40 11,15 * * MON-FRI", zone = "Asia/Shanghai")
    public void runMainSessions() {
        runScheduledDecision(LocalDate.now());
    }

    /**
     * 收盘数据同步后补算成熟的候选结果
     */
    @Scheduled(cron = "0 30 18 * * MON-FRI", zone = "Asia/Shanghai")
    public void calculateDecisionOutcomes() {
        int savedCount = decisionOutcomeService.calculatePendingOutcomes();
        log.info("智能决策结果归因定时任务完成，保存数量={}", savedCount);
    }

    void runScheduledDecision(LocalDate actionDate) {
        if (!TradingCalendar.isTradingDay(actionDate)) {
            log.info("智能决策定时任务跳过：非交易日，决策日期={}", actionDate);
            return;
        }
        SyncStartReq request = new SyncStartReq();
        request.setTaskType("DECISION");
        try {
            dataSyncJobService.startSystemTask(request);
            log.info("共享智能决策定时任务已提交，决策日期={}", actionDate);
        } catch (Exception ex) {
            log.warn("共享智能决策定时任务提交跳过，决策日期={}，原因={}", actionDate, ex.getMessage());
        }
    }
}
