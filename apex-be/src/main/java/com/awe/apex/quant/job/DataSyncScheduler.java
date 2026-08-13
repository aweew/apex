package com.awe.apex.quant.job;

import com.awe.apex.quant.domain.dto.BarSyncResp;
import com.awe.apex.quant.domain.dto.HotRefreshResp;
import com.awe.apex.quant.domain.dto.IndexRefreshResp;
import com.awe.apex.quant.domain.dto.LimitUpRefreshResp;
import com.awe.apex.quant.domain.dto.NewsRefreshResp;
import com.awe.apex.quant.domain.dto.SectorRefreshResp;
import com.awe.apex.quant.market.TradingCalendar;
import com.awe.apex.quant.service.IBarDailyService;
import com.awe.apex.quant.service.IConfigService;
import com.awe.apex.quant.service.IHotService;
import com.awe.apex.quant.service.IIndexBoardService;
import com.awe.apex.quant.service.ILimitUpLadderService;
import com.awe.apex.quant.service.IMarketBriefingService;
import com.awe.apex.quant.service.INewsService;
import com.awe.apex.quant.service.IPortfolioService;
import com.awe.apex.quant.service.ISectorBoardService;
import com.awe.apex.quant.service.IWatchlistService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

    @Resource
    private IIndexBoardService indexBoardService;

    @Resource
    private ILimitUpLadderService limitUpLadderService;

    @Resource
    private INewsService newsService;

    @Resource
    private IMarketBriefingService marketBriefingService;

    @Resource
    private IPortfolioService portfolioService;

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
            Map<String, Object> resp = watchlistService.refreshQuotes(group, 80, false);
            log.info("定时刷新行情完成 group={}, success={}", group, resp.get("successCount"));
        } catch (Exception ex) {
            log.warn("定时刷新行情失败: {}", ex.getMessage());
        }
        try {
            Map<String, Object> resp = portfolioService.refreshQuotesAll(false);
            portfolioService.snapshotAll();
            log.info("定时刷新全部组合行情完成 portfolios={}, success={}, fail={}",
                    resp.get("portfolioCount"), resp.get("success"), resp.get("fail"));
        } catch (Exception ex) {
            log.warn("定时刷新全部组合行情失败: {}", ex.getMessage());
        }
    }

    /**
     * 收盘同步包：INDEX → SECTOR → LIMIT_UP → HOT → NEWS（16:35）
     */
    @Scheduled(cron = "0 35 16 * * MON-FRI")
    public void closeBundleAfternoon() {
        if (!"true".equalsIgnoreCase(configService.getString("auto_sync_enabled", "false"))) {
            return;
        }
        if (!TradingCalendar.isTradingDay(LocalDate.now())) {
            log.info("收盘包跳过：今日非交易日");
            return;
        }
        String group = configService.getString("auto_sync_group", "我的自选");
        int ok = 0;
        int fail = 0;
        try {
            IndexRefreshResp indexResp = indexBoardService.refresh(
                    LocalDate.now().minusDays(60).format(DateTimeFormatter.BASIC_ISO_DATE));
            ok++;
            log.info("收盘包·指数完成 message={}", indexResp.getMessage());
        } catch (Exception ex) {
            fail++;
            log.warn("收盘包·指数失败: {}", ex.getMessage());
        }
        try {
            SectorRefreshResp sectorResp = sectorBoardService.refresh("INDUSTRY,CONCEPT,THEME");
            ok++;
            log.info("收盘包·板块完成 message={}", sectorResp.getMessage());
        } catch (Exception ex) {
            fail++;
            log.warn("收盘包·板块失败: {}", ex.getMessage());
        }
        try {
            LimitUpRefreshResp luResp = limitUpLadderService.refresh(null);
            ok++;
            log.info("收盘包·涨停完成 message={}", luResp.getMessage());
        } catch (Exception ex) {
            fail++;
            log.warn("收盘包·涨停失败: {}", ex.getMessage());
        }
        try {
            HotRefreshResp hotResp = hotService.refresh("eastmoney,baidu", 50);
            ok++;
            log.info("收盘包·热点完成 message={}", hotResp.getMessage());
        } catch (Exception ex) {
            fail++;
            log.warn("收盘包·热点失败: {}", ex.getMessage());
        }
        try {
            NewsRefreshResp newsResp = newsService.refresh("eastmoney,cls,ths,sina", 80);
            ok++;
            log.info("收盘包·资讯完成 message={}", newsResp.getMessage());
        } catch (Exception ex) {
            fail++;
            log.warn("收盘包·资讯失败: {}", ex.getMessage());
        }
        try {
            marketBriefingService.invalidateCache();
        } catch (Exception ex) {
            log.debug("收盘包·清简报缓存失败: {}", ex.getMessage());
        }
        // 自选快照 + 缺当日日线（一键收盘必须带个股）
        try {
            Map<String, Object> quoteResp = watchlistService.refreshQuotes(group, 80, false);
            ok++;
            log.info("收盘包·自选行情完成 success={}", quoteResp.get("successCount"));
        } catch (Exception ex) {
            fail++;
            log.warn("收盘包·自选行情失败: {}", ex.getMessage());
        }
        try {
            BarSyncResp barResp = barDailyService.syncStaleWatchlist(group, 80);
            ok++;
            log.info("收盘包·自选日线完成 success={}, fail={}",
                    barResp.getSuccessCount(), barResp.getFailCount());
        } catch (Exception ex) {
            fail++;
            log.warn("收盘包·自选日线失败: {}", ex.getMessage());
        }
        try {
            Map<String, Object> portfolioResp = portfolioService.refreshQuotesAll(false);
            int snapshotCount = portfolioService.snapshotAll();
            ok++;
            log.info("收盘包·全部组合完成 portfolios={}, success={}, fail={}, snapshot={}",
                    portfolioResp.get("portfolioCount"), portfolioResp.get("success"), portfolioResp.get("fail"), snapshotCount);
        } catch (Exception ex) {
            fail++;
            log.warn("收盘包·全部组合失败: {}", ex.getMessage());
        }
        log.info("收盘包汇总 success={}, fail={}", ok, fail);
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
