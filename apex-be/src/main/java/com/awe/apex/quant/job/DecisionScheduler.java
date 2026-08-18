package com.awe.apex.quant.job;

import com.awe.apex.quant.domain.dto.SyncStartReq;
import com.awe.apex.quant.market.TradingCalendar;
import com.awe.apex.quant.service.IDataSyncJobService;
import com.awe.apex.quant.service.IDecisionOutcomeService;
import com.awe.apex.quant.service.ApexUserAuthService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 智能决策自动调度。
 */
@Slf4j
@Component
public class DecisionScheduler {

    @Resource
    private IDataSyncJobService dataSyncJobService;

    @Resource
    private IDecisionOutcomeService decisionOutcomeService;

    @Resource
    private ApexUserAuthService userAuthService;

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
        List<Long> userIds;
        try {
            userIds = userAuthService.listEnabledUserIds();
        } catch (Exception ex) {
            log.warn("智能决策定时任务读取启用用户失败，决策日期={}，原因={}", actionDate, ex.getMessage());
            return;
        }
        for (Long userId : userIds) {
            SyncStartReq request = new SyncStartReq();
            request.setTaskType("DECISION");
            try {
                dataSyncJobService.startForUser(request, userId);
                log.info("智能决策定时任务已提交，决策日期={}，用户编号={}", actionDate, userId);
            } catch (Exception ex) {
                log.warn("智能决策定时任务提交跳过，决策日期={}，用户编号={}，原因={}",
                        actionDate, userId, ex.getMessage());
            }
        }
    }
}
