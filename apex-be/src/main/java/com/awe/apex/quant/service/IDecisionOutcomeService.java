package com.awe.apex.quant.service;

import com.awe.apex.quant.backtest.BacktestCostConfig;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.DailyAction;
import com.awe.apex.quant.domain.entity.DecisionOutcome;

import java.util.List;

/**
 * 智能决策结果归因服务
 */
public interface IDecisionOutcomeService {

    /**
     * 幂等补算尚未完整归因的操作清单
     *
     * @return 成功保存数量
     */
    int calculatePendingOutcomes();

    /**
     * 计算单条操作清单的多周期理论归因
     *
     * @param action        操作清单
     * @param stockBars     决策日后的个股日线
     * @param benchmarkBars 同期沪深300日线
     * @param costConfig    交易成本配置
     * @return 归因结果
     */
    DecisionOutcome calculateOutcome(DailyAction action, List<BarDaily> stockBars,
                                     List<BarDaily> benchmarkBars, BacktestCostConfig costConfig);
}
