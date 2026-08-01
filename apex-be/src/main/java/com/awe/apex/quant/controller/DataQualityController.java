package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.DataQualityResp;
import com.awe.apex.quant.domain.dto.WatchlistResp;
import com.awe.apex.quant.domain.entity.StrategySignalEntity;
import com.awe.apex.quant.domain.entity.UniverseSnapshot;
import com.awe.apex.quant.mapper.StrategySignalMapper;
import com.awe.apex.quant.service.IUniverseService;
import com.awe.apex.quant.service.IWatchlistService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 数据质量面板
 */
@RestController
@RequestMapping("/api/data/quality")
public class DataQualityController {

    @Resource
    private IWatchlistService watchlistService;

    @Resource
    private IUniverseService universeService;

    @Resource
    private StrategySignalMapper strategySignalMapper;

    /**
     * 自选数据健康度
     */
    @GetMapping
    public Result<DataQualityResp> overview(@RequestParam(required = false) String groupName) {
        List<WatchlistResp> list = watchlistService.listWatchlist(groupName);
        int quoted = 0;
        int ready = 0;
        int stale = 0;
        int empty = 0;
        List<String> emptyCodes = new ArrayList<>();
        List<String> unquotedCodes = new ArrayList<>();
        for (WatchlistResp row : list) {
            if (Objects.nonNull(row.getLatestPrice())) {
                quoted++;
            } else if (unquotedCodes.size() < 20) {
                unquotedCodes.add(row.getCode());
            }
            String status = row.getSyncStatus();
            if ("OK".equals(status)) {
                ready++;
            } else if ("STALE".equals(status)) {
                stale++;
            } else {
                empty++;
                if (emptyCodes.size() < 20) {
                    emptyCodes.add(row.getCode());
                }
            }
        }
        List<UniverseSnapshot> universe = universeService.latest();
        Long signalCount = strategySignalMapper.selectCount(Wrappers.<StrategySignalEntity>lambdaQuery()
                .ge(StrategySignalEntity::getSignalDate, LocalDate.now().minusDays(5)));
        String suggestion;
        if (empty > list.size() / 2) {
            suggestion = "多数股票无K线，请先运行流水线或「只同步过期」";
        } else if (quoted < list.size() / 3) {
            suggestion = "行情覆盖偏低，建议「刷新行情」";
        } else if (universe.isEmpty()) {
            suggestion = "股票池为空，请刷新股票池或跑流水线";
        } else {
            suggestion = "数据基本就绪，可运行信号/选股/批量回测";
        }
        int total = Math.max(list.size(), 1);
        BigDecimal quoteCoverage = BigDecimal.valueOf(quoted).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
        BigDecimal barsReadyCoverage = BigDecimal.valueOf(ready).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
        String slaLevel;
        if (quoteCoverage.compareTo(new BigDecimal("0.90")) >= 0
                && barsReadyCoverage.compareTo(new BigDecimal("0.85")) >= 0
                && !universe.isEmpty()) {
            slaLevel = "GREEN";
        } else if (quoteCoverage.compareTo(new BigDecimal("0.60")) >= 0
                && barsReadyCoverage.compareTo(new BigDecimal("0.50")) >= 0) {
            slaLevel = "YELLOW";
        } else {
            slaLevel = "RED";
        }
        return Result.success(DataQualityResp.builder()
                .watchlistCount(list.size())
                .quotedCount(quoted)
                .barsReadyCount(ready)
                .barsStaleCount(stale)
                .barsEmptyCount(empty)
                .universeCount(universe.size())
                .recentSignalCount(Objects.nonNull(signalCount) ? signalCount.intValue() : 0)
                .suggestion(suggestion)
                .emptyCodes(emptyCodes)
                .unquotedCodes(unquotedCodes)
                .quoteCoverage(quoteCoverage)
                .barsReadyCoverage(barsReadyCoverage)
                .slaLevel(slaLevel)
                .build());
    }
}
