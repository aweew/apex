package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.TraderPortfolioResp;
import com.awe.apex.quant.domain.dto.TraderPositionResp;
import com.awe.apex.quant.domain.dto.TraderRankingResp;
import com.awe.apex.quant.domain.entity.SmartMoneyFactor;
import com.awe.apex.quant.domain.entity.TraderProfile;
import com.awe.apex.quant.domain.dto.SmartMoneyFactorStatsResp;
import com.awe.apex.quant.service.ISmartTraderAnalyticsService;
import jakarta.annotation.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Smart Trader 查询与投影刷新接口。 */
@RestController
@RequestMapping("/api")
public class SmartTraderController {

    @Resource private ISmartTraderAnalyticsService smartTraderAnalyticsService;

    /**
     * 手工重建 Smart Trader 投影。
     *
     * @param tradeDate 快照日期
     * @return 空
     */
    @PostMapping("/smart-trader/rebuild")
    public Result<Void> rebuild(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        smartTraderAnalyticsService.rebuild(tradeDate);
        return Result.success(null);
    }

    /**
     * 查询交易者当前持仓。
     *
     * @param id 交易者ID
     * @return 持仓
     */
    @GetMapping("/traders/{id}/position")
    public Result<List<TraderPositionResp>> positions(@PathVariable Long id) { return Result.success(smartTraderAnalyticsService.positions(id)); }

    /**
     * 查询交易者账户。
     *
     * @param id 交易者ID
     * @return 账户
     */
    @GetMapping("/traders/{id}/portfolio")
    public Result<TraderPortfolioResp> portfolio(@PathVariable Long id) { return Result.success(smartTraderAnalyticsService.portfolio(id)); }

    /**
     * 查询交易者画像。
     *
     * @param id 交易者ID
     * @return 画像
     */
    @GetMapping("/traders/{id}/performance")
    public Result<TraderProfile> profile(@PathVariable Long id) { return Result.success(smartTraderAnalyticsService.profile(id)); }

    /**
     * 查询排行榜。
     *
     * @param type TOTAL / DAILY / STEADY
     * @return 排行
     */
    @GetMapping("/ranking")
    public Result<List<TraderRankingResp>> ranking(@RequestParam(required = false, defaultValue = "TOTAL") String type) { return Result.success(smartTraderAnalyticsService.ranking(type)); }

    /**
     * 查询 Smart Money 因子。
     *
     * @param tradeDate 交易日
     * @return 因子
     */
    @GetMapping("/smart-money/factors")
    public Result<List<SmartMoneyFactor>> factors(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) { return Result.success(smartTraderAnalyticsService.factors(tradeDate)); }

    /**
     * 查询 Smart Money 因子样本统计。
     *
     * @return 分持有期统计
     */
    @GetMapping("/smart-money/factor-stats")
    public Result<List<SmartMoneyFactorStatsResp>> factorStats() { return Result.success(smartTraderAnalyticsService.factorStats()); }
}
