package com.awe.apex.quant.job;

import com.awe.apex.quant.domain.dto.BarSyncResp;
import com.awe.apex.quant.domain.dto.HotRefreshResp;
import com.awe.apex.quant.domain.dto.SectorRefreshResp;
import com.awe.apex.quant.service.IBarDailyService;
import com.awe.apex.quant.service.IConfigService;
import com.awe.apex.quant.service.IHotService;
import com.awe.apex.quant.service.ISectorBoardService;
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

    @Resource
    private IHotService hotService;

    @Resource
    private ISectorBoardService sectorBoardService;

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

    /**
     * 交易时段快刷热点（跳过较慢雪球；需 auto_sync_enabled=true）
     */
    @Scheduled(cron = "0 20 9,10,11,13,14 * * MON-FRI")
    public void refreshHotIntraday() {
        if (!"true".equalsIgnoreCase(configService.getString("auto_sync_enabled", "false"))) {
            return;
        }
        try {
            HotRefreshResp resp = hotService.refresh("eastmoney,baidu", 40);
            log.info("定时热点刷新完成 message={}", resp.getMessage());
        } catch (Exception ex) {
            log.warn("定时热点刷新失败: {}", ex.getMessage());
        }
    }

    /**
     * 交易时段快刷板块榜单（需 auto_sync_enabled=true）
     */
    @Scheduled(cron = "0 25 9,10,11,13,14 * * MON-FRI")
    public void refreshSectorIntraday() {
        if (!"true".equalsIgnoreCase(configService.getString("auto_sync_enabled", "false"))) {
            return;
        }
        try {
            SectorRefreshResp resp = sectorBoardService.refresh("INDUSTRY,CONCEPT,THEME");
            log.info("定时板块刷新完成 message={}", resp.getMessage());
        } catch (Exception ex) {
            log.warn("定时板块刷新失败: {}", ex.getMessage());
        }
    }
}
