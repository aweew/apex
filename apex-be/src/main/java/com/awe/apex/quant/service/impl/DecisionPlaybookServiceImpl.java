package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.domain.dto.DecisionPlaybookResp;
import com.awe.apex.quant.domain.dto.StrategyPlaybookItem;
import com.awe.apex.quant.service.IDecisionPlaybookService;
import com.awe.apex.quant.strategy.StrategyParams;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 决策战法手册（与线上引擎 + 可配置参数对齐）
 */
@Service
public class DecisionPlaybookServiceImpl implements IDecisionPlaybookService {

    @Resource
    private StrategyParams strategyParams;

    /**
     * 获取决策战法手册
     *
     * @return 手册
     */
    @Override
    public DecisionPlaybookResp playbook() {
        int s1Fast = strategyParams.s1FastMa();
        int s1Slow = strategyParams.s1SlowMa();
        int s2Ma = strategyParams.s2Ma();
        int s3Lb = strategyParams.s3Lookback();
        String s3Vol = strategyParams.s3VolumeRatio().toPlainString();
        String s2Os = strategyParams.s2RsiOversold().toPlainString();
        String s2Rb = strategyParams.s2RsiRebound().toPlainString();
        String s2Ob = strategyParams.s2RsiOverbought().toPlainString();
        int cfWindow = strategyParams.decisionConfluenceWindow();
        int cfMin = strategyParams.decisionConfluenceMinStrategies();

        List<StrategyPlaybookItem> strategies = List.of(
                StrategyPlaybookItem.builder()
                        .strategyId("S1")
                        .name("均线趋势")
                        .style("趋势")
                        .buyRule("MA" + s1Fast + " 上穿 MA" + s1Slow + "，且当日成交量 > "
                                + strategyParams.s1VolMa() + " 日均量；强度分随金叉幅度/量比连续变化（约 65–92）")
                        .exitRule("收盘价跌破 MA" + s1Fast + " 离场")
                        .marketFit("趋势市、普涨扩散；震荡市假信号增多")
                        .defaultScore(68)
                        .inDecisionPipeline(true)
                        .paramsHint("strategy.s1.fast_ma / slow_ma / vol_ma")
                        .build(),
                StrategyPlaybookItem.builder()
                        .strategyId("S2")
                        .name("RSI 回调")
                        .style("回调")
                        .buyRule("价格在 MA" + s2Ma + " 上方，RSI" + strategyParams.s2RsiPeriod()
                                + " 从 <" + s2Os + " 回升至 >" + s2Rb + "；贴近均线+放量加分")
                        .exitRule("RSI>" + s2Ob + " 或收盘价跌破 MA" + s2Ma + " 离场")
                        .marketFit("上升趋势中的短线回调；弱市慎用；低估值联动优先")
                        .defaultScore(65)
                        .inDecisionPipeline(true)
                        .paramsHint("strategy.s2.ma / rsi_period / rsi_oversold / rsi_rebound / rsi_overbought")
                        .build(),
                StrategyPlaybookItem.builder()
                        .strategyId("S3")
                        .name("突破放量")
                        .style("突破")
                        .buyRule("收盘创 " + s3Lb + " 日新高，且量比 >" + s3Vol + "；突破幅度与超额量比决定强度")
                        .exitRule("收盘跌破最近一次突破日最低价离场（日频已产出 SELL）")
                        .marketFit("情绪偏暖、主线明确时；高估突破会降权且禁止直接可执行")
                        .defaultScore(68)
                        .inDecisionPipeline(true)
                        .paramsHint("strategy.s3.lookback / volume_ratio")
                        .build()
        );

        return DecisionPlaybookResp.builder()
                .pipelineSteps(List.of(
                        "1. 市场简报：大盘/趋势/量能/风格/广度/涨停 → 进攻|均衡|防守，调节买入仓位系数",
                        "2. 扫描池：全 A 市值优先 + 主线行业补扫 + 热点 Top50 + 持仓"
                                + "（主线排除昨日连板/涨停等结果型板；打分偏资金与 3/5 日持续性，非纯当日涨幅）",
                        "3. 跑策略：S1/S2/S3 产出连续强度 BUY/SELL（参数可在系统配置改）",
                        "4. 多策略共振：近 " + cfWindow + " 日同向 ≥" + cfMin + " 策略加分",
                        "5. 热点/主线/基本面/估值：DecisionScorer 统一加减分与仓位",
                        "6. 估值×策略联动：低估+S2 提权；高估+S3 降权且不可直接 TRIGGERED",
                        "7. 落库：买卖持清单 + 估值/联动字段 + 简报快照；同步观察池"
                ))
                .strategies(strategies)
                .scoreRules(List.of(
                        "基础分 = 策略连续强度分（约 65–92，缺省 60）",
                        "多策略共振（≥" + cfMin + "）+" + strategyParams.decisionScoreConfluence().toPlainString(),
                        "热点共振（≥2 源）+" + strategyParams.decisionScoreHot().toPlainString()
                                + "；≥3 源再 +" + strategyParams.decisionScoreHotTriple().toPlainString(),
                        "命中当日主线 +" + strategyParams.decisionScoreMainline().toPlainString()
                                + "；有主线但未命中 −" + strategyParams.decisionScoreOffMainline().toPlainString(),
                        "基本面偏弱 −" + strategyParams.decisionScoreFundPenalty().toPlainString()
                                + "；市场「防守」−" + strategyParams.decisionScoreDefense().toPlainString()
                                + "；「进攻」且共振 +" + strategyParams.decisionScoreOffense().toPlainString(),
                        "低估/偏低 + S2 → +" + strategyParams.decisionLinkUndervaluedS2().toPlainString() + "（低估回调优先）",
                        "高估/偏高 + S3 → −" + strategyParams.decisionLinkOvervaluedS3().toPlainString() + "（禁止高分直接可执行）",
                        "估值档位另有 scoreDelta（约 −12～+12）并入综合分"
                ))
                .positionRules(List.of(
                        "默认建议仓位 10%",
                        "共振/热点/主线且基本面 OK → 抬至约 12%～单票上限",
                        "命中主线 ×1.08；逆主线 ×0.85",
                        "明显高估 ×0.70；偏高 ×0.85；明显低估 ×1.08",
                        "再乘市场 buyWeightFactor（进攻 1.10 / 均衡 1.00 / 防守 0.55；数据不足 0.40）",
                        "最后不超过风控单票上限"
                ))
                .fundRules(List.of(
                        "ROE < 3% 或负债率 > 80% → 剔除买入，仅进观察池",
                        "ROE < 8% 或负债率 > 70% → 基本面偏弱，降权"
                ))
                .sellRules(List.of(
                        "优先级：持仓止损/止盈触价 → 策略 SELL → 其余 HOLD",
                        "策略卖出信号只作用于「我的持仓」，未持仓忽略",
                        "S1 跌破快线；S2 RSI超买/跌破均线；S3 跌破突破日低点",
                        "止损/止盈卖出评分固定 90，优先处理",
                        "持仓缺止损或止盈时，按 ATR14×倍数（或 -8%/+20%）自动生成并回写",
                        "买入/持有决策的离场规则必含「止损价 / 止盈价」"
                ))
                .message("决策战法手册 · 策略 strengthScore + DecisionScorer 联动 · 配置键 strategy.* / decision.*")
                .build();
    }
}
