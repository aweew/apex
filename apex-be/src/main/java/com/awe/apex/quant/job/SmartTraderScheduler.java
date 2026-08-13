package com.awe.apex.quant.job;

import com.awe.apex.quant.service.ISmartTraderAnalyticsService;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Smart Trader 日终投影刷新。 */
@Component
public class SmartTraderScheduler {

    @Resource private ISmartTraderAnalyticsService smartTraderAnalyticsService;

    /**
     * 交易日收盘后刷新持仓、账户、排名、画像及因子。
     */
    @Scheduled(cron = "0 30 16 * * MON-FRI", zone = "Asia/Shanghai")
    public void rebuildAfterClose() { smartTraderAnalyticsService.rebuild(LocalDate.now()); }
}
