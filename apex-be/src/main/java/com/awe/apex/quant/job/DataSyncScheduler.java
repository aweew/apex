package com.awe.apex.quant.job;

import com.awe.apex.quant.domain.dto.BarSyncResp;
import com.awe.apex.quant.service.IBarDailyService;
import com.awe.apex.quant.service.IConfigService;
import com.awe.apex.quant.service.IWatchlistService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 可选定时同步（默认关闭，配置 auto_sync_enabled=true 开启）
 */
@Slf4j
@Component
public class DataSyncScheduler {

    @Resource
    private IConfigService configService;

    @Resource
    private IBarDailyService barDailyService;

    @Resource
    private IWatchlistService watchlistService;

    /**
     * 工作日傍晚尝试同步过期日线
     */
    @Scheduled(cron = "0 30 18 * * MON-FRI")
    public void syncStaleEvening() {
        if (!"true".equalsIgnoreCase(configService.getString("auto_sync_enabled", "false"))) {
            return;
        }
        String group = configService.getString("auto_sync_group", "我的自选");
        try {
            BarSyncResp resp = barDailyService.syncStaleWatchlist(group, 40);
            log.info("定时同步日线完成 group={}, success={}, fail={}",
                    group, resp.getSuccessCount(), resp.getFailCount());
        } catch (Exception ex) {
            log.warn("定时同步日线失败: {}", ex.getMessage());
        }
    }

    /**
     * 工作日收盘后刷新部分行情
     */
    @Scheduled(cron = "0 10 16 * * MON-FRI")
    public void refreshQuotesAfternoon() {
        if (!"true".equalsIgnoreCase(configService.getString("auto_sync_enabled", "false"))) {
            return;
        }
        String group = configService.getString("auto_sync_group", "我的自选");
        try {
            Map<String, Object> resp = watchlistService.refreshQuotes(group, 40, true);
            log.info("定时刷新行情完成 group={}, success={}", group, resp.get("successCount"));
        } catch (Exception ex) {
            log.warn("定时刷新行情失败: {}", ex.getMessage());
        }
    }
}
