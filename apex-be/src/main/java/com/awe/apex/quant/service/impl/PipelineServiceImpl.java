package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.BarSyncResp;
import com.awe.apex.quant.domain.dto.DecisionRunReq;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import com.awe.apex.quant.domain.dto.PipelineRunReq;
import com.awe.apex.quant.domain.dto.PipelineRunResp;
import com.awe.apex.quant.domain.dto.SignalRunReq;
import com.awe.apex.quant.domain.dto.UniverseRefreshReq;
import com.awe.apex.quant.domain.dto.UniverseRefreshResp;
import com.awe.apex.quant.domain.entity.DailyAction;
import com.awe.apex.quant.domain.entity.StrategySignalEntity;
import com.awe.apex.quant.market.TradingCalendar;
import com.awe.apex.quant.service.IBarDailyService;
import com.awe.apex.quant.service.IDailyActionService;
import com.awe.apex.quant.service.IDecisionService;
import com.awe.apex.quant.service.IPipelineService;
import com.awe.apex.quant.service.ISignalService;
import com.awe.apex.quant.service.IUniverseService;
import com.awe.apex.quant.service.IWatchlistService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 一键研究流水线：行情/日线准备 →（智能决策 或 旧日终路径）
 */
@Service
public class PipelineServiceImpl implements IPipelineService {

    @Resource
    private IWatchlistService watchlistService;

    @Resource
    private IBarDailyService barDailyService;

    @Resource
    private IUniverseService universeService;

    @Resource
    private ISignalService signalService;

    @Resource
    private IDailyActionService dailyActionService;

    @Resource
    private IDecisionService decisionService;

    /**
     * 运行流水线
     *
     * @param req 请求
     * @return 结果
     */
    @Override
    public PipelineRunResp run(PipelineRunReq req) {
        PipelineRunReq safe = Objects.nonNull(req) ? req : new PipelineRunReq();
        String groupName = StringUtils.isNotBlank(safe.getGroupName()) ? safe.getGroupName() : "我的自选";
        boolean refreshQuotes = !Boolean.FALSE.equals(safe.getRefreshQuotes());
        boolean syncStale = !Boolean.FALSE.equals(safe.getSyncStaleBars());
        boolean runDecision = Boolean.TRUE.equals(safe.getRunDecision());
        // 智能决策已内含股票池/信号/日终研判；开启时默认跳过旧三段，避免重复扫描
        boolean refreshUniverse = runDecision ? Boolean.TRUE.equals(safe.getRefreshUniverse())
                : !Boolean.FALSE.equals(safe.getRefreshUniverse());
        boolean runSignals = runDecision ? Boolean.TRUE.equals(safe.getRunSignals())
                : !Boolean.FALSE.equals(safe.getRunSignals());
        boolean runDaily = runDecision ? Boolean.TRUE.equals(safe.getRunDaily())
                : Boolean.TRUE.equals(safe.getRunDaily());

        List<String> steps = new ArrayList<>();
        Integer quoteSuccess = null;
        Integer barSuccess = null;
        Integer barFail = null;
        Integer universeCount = null;
        String batchNo = null;
        Integer signalCount = null;
        Integer dailyCount = null;
        Integer decisionBuyCount = null;
        Integer decisionSellCount = null;
        Integer decisionHoldCount = null;
        Integer decisionExecutableCount = null;
        Integer decisionValuationCheapCount = null;
        Integer observeUpserted = null;

        if (refreshQuotes) {
            Map<String, Object> quote = watchlistService.refreshQuotes(groupName, 40, true);
            quoteSuccess = (Integer) quote.get("successCount");
            steps.add("刷新行情 " + quoteSuccess + " 只");
        }
        if (syncStale) {
            BarSyncResp barSync = barDailyService.syncStaleWatchlist(groupName, 40);
            barSuccess = barSync.getSuccessCount();
            barFail = barSync.getFailCount();
            steps.add("同步过期/缺失日线 成功" + barSuccess + " 失败" + barFail);
        }
        if (refreshUniverse) {
            UniverseRefreshReq universeReq = new UniverseRefreshReq();
            universeReq.setScope("MARKET");
            universeReq.setLooseFilter(true);
            universeReq.setGroupName(groupName);
            UniverseRefreshResp universe = universeService.refresh(universeReq);
            universeCount = universe.getCount();
            batchNo = universe.getBatchNo();
            steps.add("股票池入选 " + universeCount + " 批号 " + batchNo);
        }
        if (runSignals) {
            SignalRunReq signalReq = new SignalRunReq();
            signalReq.setUseUniverse(true);
            List<StrategySignalEntity> signals = signalService.run(signalReq);
            signalCount = signals.size();
            steps.add("生成信号 " + signalCount + " 条");
        }
        if (runDaily) {
            LocalDate actionDate = TradingCalendar.latestTradingDayOnOrBefore(LocalDate.now());
            List<DailyAction> actions = dailyActionService.run(actionDate);
            dailyCount = actions.size();
            steps.add("日终清单 " + dailyCount + " 条@" + actionDate);
        }
        if (runDecision) {
            DecisionRunReq decisionReq = new DecisionRunReq();
            decisionReq.setGroupName(groupName);
            DecisionTodayResp decision = decisionService.run(decisionReq);
            int buys = CollUtil.isEmpty(decision.getBuys()) ? 0 : decision.getBuys().size();
            int sells = CollUtil.isEmpty(decision.getSells()) ? 0 : decision.getSells().size();
            int holds = CollUtil.isEmpty(decision.getHolds()) ? 0 : decision.getHolds().size();
            decisionBuyCount = Objects.nonNull(decision.getBuyCount()) ? decision.getBuyCount() : buys;
            decisionSellCount = Objects.nonNull(decision.getSellCount()) ? decision.getSellCount() : sells;
            decisionHoldCount = Objects.nonNull(decision.getHoldCount()) ? decision.getHoldCount() : holds;
            decisionExecutableCount = Objects.nonNull(decision.getExecutableCount()) ? decision.getExecutableCount() : 0;
            decisionValuationCheapCount = Objects.nonNull(decision.getValuationCheapCount())
                    ? decision.getValuationCheapCount() : 0;
            observeUpserted = decision.getObserveUpserted();
            if (Objects.nonNull(decision.getUniverseCount())) {
                universeCount = decision.getUniverseCount();
            }
            String obs = "";
            if (Objects.nonNull(observeUpserted) && observeUpserted > 0) {
                obs = " · 观察池写入" + observeUpserted;
            }
            steps.add("智能决策 买" + decisionBuyCount + "/卖" + decisionSellCount + "/持" + decisionHoldCount
                    + " · 可执行" + decisionExecutableCount
                    + " · 低估" + decisionValuationCheapCount
                    + obs);
        }
        if (steps.isEmpty()) {
            steps.add("未选择任何步骤");
        }

        return PipelineRunResp.builder()
                .groupName(groupName)
                .quoteSuccess(quoteSuccess)
                .barSuccess(barSuccess)
                .barFail(barFail)
                .universeCount(universeCount)
                .universeBatchNo(batchNo)
                .signalCount(signalCount)
                .dailyCount(dailyCount)
                .decisionBuyCount(decisionBuyCount)
                .decisionSellCount(decisionSellCount)
                .decisionHoldCount(decisionHoldCount)
                .decisionExecutableCount(decisionExecutableCount)
                .decisionValuationCheapCount(decisionValuationCheapCount)
                .observeUpserted(observeUpserted)
                .steps(steps)
                .build();
    }
}
