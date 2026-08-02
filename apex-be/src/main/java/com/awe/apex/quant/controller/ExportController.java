package com.awe.apex.quant.controller;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.domain.dto.EquityPointResp;
import com.awe.apex.quant.domain.dto.FactorExposureResp;
import com.awe.apex.quant.domain.dto.MonteCarloResp;
import com.awe.apex.quant.domain.dto.PaperCostResp;
import com.awe.apex.quant.domain.dto.PaperExposureResp;
import com.awe.apex.quant.domain.dto.PaperPerformanceResp;
import com.awe.apex.quant.domain.dto.PositionWeightResp;
import com.awe.apex.quant.domain.dto.WatchlistResp;
import com.awe.apex.quant.domain.entity.BacktestTrade;
import com.awe.apex.quant.domain.entity.JournalTrade;
import com.awe.apex.quant.domain.entity.PaperOrder;
import com.awe.apex.quant.domain.dto.DecisionItemResp;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import com.awe.apex.quant.domain.dto.ObservePoolResp;
import com.awe.apex.quant.domain.dto.SignalItemResp;
import com.awe.apex.quant.domain.entity.UniverseSnapshot;
import com.awe.apex.quant.service.IBacktestService;
import com.awe.apex.quant.service.IDecisionService;
import com.awe.apex.quant.service.IJournalService;
import com.awe.apex.quant.service.IObservePoolService;
import com.awe.apex.quant.service.IPaperService;
import com.awe.apex.quant.service.ISignalService;
import com.awe.apex.quant.service.IUniverseService;
import com.awe.apex.quant.service.IWatchlistService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * 导出 CSV
 */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    @Resource
    private IBacktestService backtestService;

    @Resource
    private IJournalService journalService;

    @Resource
    private IPaperService paperService;

    @Resource
    private ISignalService signalService;

    @Resource
    private IUniverseService universeService;

    @Resource
    private IWatchlistService watchlistService;

    @Resource
    private IDecisionService decisionService;

    @Resource
    private IObservePoolService observePoolService;

    /**
     * 导出观察池
     *
     * @param status   状态；空则排除归档
     * @param side     方向
     * @param response HTTP 响应
     */
    @GetMapping("/observe")
    public void exportObserve(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String side,
            HttpServletResponse response) {
        try {
            List<ObservePoolResp> rows = observePoolService.list(status, side, null);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=observe_pool.csv");
            PrintWriter writer = response.getWriter();
            writer.println("code,name,side,status,priority,reason,strategy_id,trigger_type,trigger_price,"
                    + "stop_loss,target_price,valuation_level,valuation_label,valuation_score,tags,note");
            for (ObservePoolResp row : rows) {
                writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                        row.getCode(),
                        safe(row.getName()),
                        safe(row.getSide()),
                        safe(row.getStatus()),
                        row.getPriority(),
                        safe(row.getReason()),
                        safe(row.getStrategyId()),
                        safe(row.getTriggerType()),
                        row.getTriggerPrice(),
                        row.getStopLoss(),
                        row.getTargetPrice(),
                        safe(row.getValuationLevel()),
                        safe(row.getValuationLabel()),
                        row.getValuationScore(),
                        safe(row.getTags()),
                        safe(row.getNote()));
            }
            writer.flush();
        } catch (Exception ex) {
            throw new BusinessException("导出失败: " + ex.getMessage());
        }
    }

    /**
     * 导出今日决策清单
     *
     * @param date      决策日，默认今天
     * @param groupName 自选分组
     * @param response  HTTP 响应
     */
    @GetMapping("/decision")
    public void exportDecision(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String groupName,
            HttpServletResponse response) {
        try {
            LocalDate actionDate = Objects.nonNull(date) ? date : LocalDate.now();
            DecisionTodayResp today = decisionService.today(actionDate, groupName);
            List<DecisionItemResp> items = Objects.nonNull(today.getItems()) ? today.getItems() : List.of();
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition",
                    "attachment; filename=decision_" + actionDate + ".csv");
            PrintWriter writer = response.getWriter();
            writer.println("action_date,code,name,action,strategy_id,score,suggested_weight,"
                    + "valuation_level,valuation_label,executable_hint,mainline_match,link_hint,reason,exit_rule");
            for (DecisionItemResp item : items) {
                writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                        actionDate,
                        item.getCode(),
                        safe(item.getName()),
                        safe(item.getAction()),
                        safe(item.getStrategyId()),
                        item.getScore(),
                        item.getSuggestedWeight(),
                        safe(item.getValuationLevel()),
                        safe(item.getValuationLabel()),
                        item.getExecutableHint(),
                        item.getMainlineMatch(),
                        safe(item.getLinkHint()),
                        safe(item.getReason()),
                        safe(item.getExitRule()));
            }
            writer.flush();
        } catch (Exception ex) {
            throw new BusinessException("导出失败: " + ex.getMessage());
        }
    }

    /**
     * 导出回测成交
     */
    @GetMapping("/backtest/{jobId}")
    public void exportBacktest(@PathVariable Long jobId, HttpServletResponse response) {
        try {
            List<BacktestTrade> trades = backtestService.listTrades(jobId);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=backtest_" + jobId + ".csv");
            PrintWriter writer = response.getWriter();
            writer.println("trade_date,side,price,quantity,amount,fee,reason");
            for (BacktestTrade trade : trades) {
                writer.printf("%s,%s,%s,%s,%s,%s,%s%n",
                        trade.getTradeDate(), trade.getSide(), trade.getPrice(), trade.getQuantity(),
                        trade.getAmount(), trade.getFee(), safe(trade.getReason()));
            }
            writer.flush();
        } catch (Exception ex) {
            throw new BusinessException("导出失败: " + ex.getMessage());
        }
    }

    /**
     * 导出纸面绩效与权益曲线
     */
    @GetMapping("/paper/performance")
    public void exportPaperPerformance(@RequestParam(required = false) Long accountId,
                                       @RequestParam(required = false, defaultValue = "000300") String benchmarkCode,
                                       HttpServletResponse response) {
        try {
            PaperPerformanceResp perf = paperService.performance(accountId, benchmarkCode, "000905");
            PaperExposureResp exposure = paperService.exposure(accountId);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=paper_performance.csv");
            PrintWriter writer = response.getWriter();
            writer.println("section,key,value");
            writer.printf("summary,paper_return,%s%n", perf.getPaperReturn());
            writer.printf("summary,time_weighted_return,%s%n", perf.getTimeWeightedReturn());
            writer.printf("summary,benchmark_return,%s%n", perf.getBenchmarkReturn());
            writer.printf("summary,alpha,%s%n", perf.getAlpha());
            writer.printf("summary,rolling_alpha_20,%s%n", perf.getRollingAlpha20());
            writer.printf("summary,beta,%s%n", perf.getBeta());
            writer.printf("summary,sortino,%s%n", perf.getSortino());
            writer.printf("summary,information_ratio,%s%n", perf.getInformationRatio());
            writer.printf("summary,tracking_error,%s%n", perf.getTrackingError());
            writer.printf("summary,max_drawdown,%s%n", perf.getMaxDrawdown());
            writer.printf("summary,sharpe,%s%n", perf.getSharpe());
            writer.printf("summary,rolling_beta_20,%s%n", perf.getRollingBeta20());
            writer.printf("summary,alt_benchmark,%s%n", perf.getAltBenchmarkCode());
            writer.printf("summary,alt_alpha,%s%n", perf.getAltAlpha());
            writer.printf("summary,top1_weight,%s%n", exposure.getTop1Weight());
            writer.printf("summary,herfindahl,%s%n", exposure.getHerfindahl());
            PaperCostResp cost = paperService.costSummary(accountId);
            writer.printf("summary,total_fee,%s%n", cost.getTotalFee());
            writer.printf("summary,fee_rate,%s%n", cost.getFeeRate());
            MonteCarloResp mc = paperService.monteCarlo(accountId, 300, 20);
            writer.printf("summary,mc_p5,%s%n", mc.getTerminalReturnP5());
            writer.printf("summary,mc_p50,%s%n", mc.getTerminalReturnP50());
            writer.printf("summary,mc_dd_p95,%s%n", mc.getMaxDrawdownP95());
            FactorExposureResp factor = paperService.factorExposure(accountId);
            writer.printf("summary,factor_momentum20,%s%n", factor.getMomentum20());
            writer.printf("summary,factor_volatility20,%s%n", factor.getVolatility20());
            writer.printf("summary,factor_rs20,%s%n", factor.getRs20VsHs300());
            writer.println("equity,trade_date,equity");
            if (perf.getPaperEquities() != null) {
                for (EquityPointResp point : perf.getPaperEquities()) {
                    writer.printf("equity,%s,%s%n", point.getTradeDate(), point.getEquity());
                }
            }
            writer.println("position,code,weight,market_value,pnl");
            if (exposure.getPositions() != null) {
                for (PositionWeightResp row : exposure.getPositions()) {
                    writer.printf("position,%s,%s,%s,%s%n",
                            row.getCode(), row.getWeight(), row.getMarketValue(), row.getPnl());
                }
            }
            writer.flush();
        } catch (Exception ex) {
            throw new BusinessException("导出失败: " + ex.getMessage());
        }
    }

    /**
     * 导出模拟盘订单
     */
    @GetMapping("/paper/orders")
    public void exportPaperOrders(@RequestParam(required = false) Long accountId,
                                  HttpServletResponse response) {
        try {
            Long id = accountId;
            if (id == null) {
                id = paperService.defaultAccount().getId();
            }
            List<PaperOrder> orders = paperService.listOrders(id);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=paper_orders.csv");
            PrintWriter writer = response.getWriter();
            writer.println("trade_date,code,side,price,quantity,amount,fee,status,reason");
            for (PaperOrder order : orders) {
                writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                        order.getTradeDate(), order.getCode(), order.getSide(), order.getPrice(),
                        order.getQuantity(), order.getAmount(), order.getFee(), order.getStatus(),
                        safe(order.getReason()));
            }
            writer.flush();
        } catch (Exception ex) {
            throw new BusinessException("导出失败: " + ex.getMessage());
        }
    }

    /**
     * 导出最近信号
     */
    @GetMapping("/signals")
    public void exportSignals(@RequestParam(defaultValue = "200") Integer limit,
                              @RequestParam(defaultValue = "true") Boolean dedupeByCode,
                              HttpServletResponse response) {
        try {
            List<SignalItemResp> signals = signalService.toItemRespList(
                    signalService.latest(limit, Boolean.TRUE.equals(dedupeByCode)));
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=signals.csv");
            PrintWriter writer = response.getWriter();
            writer.println("signal_date,code,name,strategy_id,side,score,reason_json");
            for (SignalItemResp signal : signals) {
                writer.printf("%s,%s,%s,%s,%s,%s,%s%n",
                        signal.getSignalDate(), signal.getCode(), safe(signal.getName()), signal.getStrategyId(),
                        signal.getSide(), signal.getScore(), safe(signal.getReasonJson()));
            }
            writer.flush();
        } catch (Exception ex) {
            throw new BusinessException("导出失败: " + ex.getMessage());
        }
    }

    /**
     * 导出自选（含行情快照）
     */
    @GetMapping("/watchlist")
    public void exportWatchlist(@RequestParam(required = false) String groupName,
                                HttpServletResponse response) {
        try {
            List<WatchlistResp> list = watchlistService.listWatchlist(groupName);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=watchlist.csv");
            PrintWriter writer = response.getWriter();
            writer.println("code,name,market,group_name,latest_price,pct_chg,pe_ttm,pb,circ_mv,industry,last_bar_date,bar_count,sync_status");
            for (WatchlistResp row : list) {
                writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                        row.getCode(), safe(row.getName()), safe(row.getMarket()), safe(row.getGroupName()),
                        row.getLatestPrice(), row.getPctChg(), row.getPeTtm(), row.getPb(), row.getCircMv(),
                        safe(row.getIndustry()), row.getLastBarDate(), row.getBarCount(), row.getSyncStatus());
            }
            writer.flush();
        } catch (Exception ex) {
            throw new BusinessException("导出失败: " + ex.getMessage());
        }
    }

    /**
     * 导出最新股票池
     */
    @GetMapping("/universe")
    public void exportUniverse(HttpServletResponse response) {
        try {
            List<UniverseSnapshot> list = universeService.latest();
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=universe.csv");
            PrintWriter writer = response.getWriter();
            writer.println("batch_no,code,name,reason_tags");
            for (UniverseSnapshot item : list) {
                writer.printf("%s,%s,%s,%s%n",
                        safe(item.getBatchNo()), item.getCode(), safe(item.getName()),
                        safe(item.getReasonTags()));
            }
            writer.flush();
        } catch (Exception ex) {
            throw new BusinessException("导出失败: " + ex.getMessage());
        }
    }

    /**
     * 导出人工成交
     */
    @GetMapping("/journal")
    public void exportJournal(HttpServletResponse response) {
        try {
            List<JournalTrade> trades = journalService.latest(500);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=journal.csv");
            PrintWriter writer = response.getWriter();
            writer.println("trade_date,code,side,price,quantity,amount,related_action_id,note");
            for (JournalTrade trade : trades) {
                writer.printf("%s,%s,%s,%s,%s,%s,%s,%s%n",
                        trade.getTradeDate(), trade.getCode(), trade.getSide(), trade.getPrice(),
                        trade.getQuantity(), trade.getAmount(), trade.getRelatedActionId(), safe(trade.getNote()));
            }
            writer.flush();
        } catch (Exception ex) {
            throw new BusinessException("导出失败: " + ex.getMessage());
        }
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(",", "，").replace("\n", " ");
    }
}
