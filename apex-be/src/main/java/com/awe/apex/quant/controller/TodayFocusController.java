package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.HotConfluenceItem;
import com.awe.apex.quant.domain.dto.HotOverviewResp;
import com.awe.apex.quant.domain.dto.RiskAlertItem;
import com.awe.apex.quant.domain.dto.RiskOverviewResp;
import com.awe.apex.quant.domain.dto.SignalConfluenceItem;
import com.awe.apex.quant.domain.dto.SignalConfluenceResp;
import com.awe.apex.quant.domain.dto.TodayFocusResp;
import com.awe.apex.quant.domain.dto.WatchlistMoverResp;
import com.awe.apex.quant.domain.dto.WatchlistResp;
import com.awe.apex.quant.domain.entity.StrategySignalEntity;
import com.awe.apex.quant.service.IHotService;
import com.awe.apex.quant.service.IPaperService;
import com.awe.apex.quant.service.IRiskService;
import com.awe.apex.quant.service.ISignalService;
import com.awe.apex.quant.service.IWatchlistService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 今日关注清单
 */
@RestController
@RequestMapping("/api/focus/today")
public class TodayFocusController {

    @Resource
    private IWatchlistService watchlistService;

    @Resource
    private ISignalService signalService;

    @Resource
    private IRiskService riskService;

    @Resource
    private IPaperService paperService;

    @Resource
    private IHotService hotService;

    /**
     * 汇总异动、信号、风控、热点共振
     */
    @GetMapping
    public Result<TodayFocusResp> today(@RequestParam(required = false) String groupName,
                                        @RequestParam(required = false, defaultValue = "5") BigDecimal moverThreshold,
                                        @RequestParam(required = false, defaultValue = "70") BigDecimal minScore) {
        String group = Objects.nonNull(groupName) ? groupName : "我的自选";
        WatchlistMoverResp movers = watchlistService.movers(group, moverThreshold, 8);
        List<StrategySignalEntity> all = signalService.latest(80, true);
        List<StrategySignalEntity> buys = new ArrayList<>();
        List<StrategySignalEntity> sells = new ArrayList<>();
        for (StrategySignalEntity signal : all) {
            if (Objects.nonNull(minScore) && (Objects.isNull(signal.getScore()) || signal.getScore().compareTo(minScore) < 0)) {
                continue;
            }
            if ("BUY".equalsIgnoreCase(signal.getSide()) && buys.size() < 8) {
                buys.add(signal);
            } else if ("SELL".equalsIgnoreCase(signal.getSide()) && sells.size() < 8) {
                sells.add(signal);
            }
        }
        Long accountId = paperService.defaultAccount().getId();
        RiskOverviewResp risk = riskService.overview(accountId);
        List<String> alerts = new ArrayList<>();
        if (risk.getAlerts() != null) {
            for (RiskAlertItem item : risk.getAlerts()) {
                alerts.add("[" + item.getLevel() + "] " + item.getMessage());
                if (alerts.size() >= 10) {
                    break;
                }
            }
        }
        List<WatchlistResp> list = watchlistService.listWatchlist(group);
        int advance = 0;
        int decline = 0;
        for (WatchlistResp row : list) {
            if (Objects.nonNull(row.getPctChg())) {
                if (row.getPctChg().signum() > 0) {
                    advance++;
                } else if (row.getPctChg().signum() < 0) {
                    decline++;
                }
            }
        }
        BigDecimal ad = decline == 0 ? BigDecimal.valueOf(advance)
                : BigDecimal.valueOf(advance).divide(BigDecimal.valueOf(decline), 2, RoundingMode.HALF_UP);
        String breadthMsg = "涨 " + advance + " / 跌 " + decline + " · A/D " + ad;
        SignalConfluenceResp confl = signalService.confluence(5, 2);
        List<SignalConfluenceItem> conflItems = confl.getItems() == null ? List.of() : confl.getItems();
        if (conflItems.size() > 8) {
            conflItems = conflItems.subList(0, 8);
        }
        HotOverviewResp hotOverview = hotService.overview(40);
        List<HotConfluenceItem> hotItems = hotOverview.getConfluence() == null ? List.of() : hotOverview.getConfluence();
        if (hotItems.size() > 8) {
            hotItems = hotItems.subList(0, 8);
        }

        return Result.success(TodayFocusResp.builder()
                .movers(movers)
                .buySignals(buys)
                .sellSignals(sells)
                .riskAlerts(alerts)
                .breadthMessage(breadthMsg)
                .confluence(conflItems)
                .hotConfluence(hotItems)
                .message("异动 " + ((movers.getGainers() == null ? 0 : movers.getGainers().size())
                        + (movers.getLosers() == null ? 0 : movers.getLosers().size()))
                        + " · BUY " + buys.size() + " · SELL " + sells.size()
                        + " · 策略共振 " + conflItems.size()
                        + " · 热点共振 " + hotItems.size()
                        + " · 风控 " + alerts.size())
                .build());
    }
}
