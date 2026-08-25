package com.awe.apex.quant.strategy;

import com.awe.apex.quant.service.IConfigService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 策略可配置参数（缺省回落硬编码默认值）
 */
@Component
public class StrategyParams {

    @Resource
    private IConfigService configService;

    /**
     * 整数参数
     *
     * @param key          配置键
     * @param defaultValue 默认
     * @return 值
     */
    public int getInt(String key, int defaultValue) {
        if (Objects.isNull(configService)) {
            return defaultValue;
        }
        String raw = configService.getString(key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    /**
     * 小数参数
     *
     * @param key          配置键
     * @param defaultValue 默认
     * @return 值
     */
    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        if (Objects.isNull(configService)) {
            return defaultValue;
        }
        BigDecimal v = configService.getDecimal(key, defaultValue);
        return Objects.nonNull(v) ? v : defaultValue;
    }

    public int s1FastMa() {
        return getInt("strategy.s1.fast_ma", 20);
    }

    public int s1SlowMa() {
        return getInt("strategy.s1.slow_ma", 60);
    }

    public int s1VolMa() {
        return getInt("strategy.s1.vol_ma", 20);
    }

    public int s2Ma() {
        return getInt("strategy.s2.ma", 60);
    }

    public int s2RsiPeriod() {
        return getInt("strategy.s2.rsi_period", 14);
    }

    public BigDecimal s2RsiOversold() {
        return getDecimal("strategy.s2.rsi_oversold", new BigDecimal("30"));
    }

    public BigDecimal s2RsiRebound() {
        return getDecimal("strategy.s2.rsi_rebound", new BigDecimal("35"));
    }

    public BigDecimal s2RsiOverbought() {
        return getDecimal("strategy.s2.rsi_overbought", new BigDecimal("70"));
    }

    public int s3Lookback() {
        return getInt("strategy.s3.lookback", 20);
    }

    public BigDecimal s3VolumeRatio() {
        return getDecimal("strategy.s3.volume_ratio", new BigDecimal("1.5"));
    }

    public BigDecimal decisionScoreConfluence() {
        return getDecimal("decision.score.confluence", new BigDecimal("12"));
    }

    public BigDecimal decisionScoreHot() {
        return getDecimal("decision.score.hot", new BigDecimal("8"));
    }

    public BigDecimal decisionScoreHotTriple() {
        return getDecimal("decision.score.hot_triple", new BigDecimal("4"));
    }

    public BigDecimal decisionScoreMainline() {
        return getDecimal("decision.score.mainline", new BigDecimal("10"));
    }

    public BigDecimal decisionScoreOffMainline() {
        return getDecimal("decision.score.off_mainline", new BigDecimal("5"));
    }

    public BigDecimal decisionScoreFundPenalty() {
        return getDecimal("decision.score.fund_penalty", new BigDecimal("8"));
    }

    public BigDecimal decisionScoreDefense() {
        return getDecimal("decision.score.defense", new BigDecimal("6"));
    }

    public BigDecimal decisionScoreOffense() {
        return getDecimal("decision.score.offense", new BigDecimal("3"));
    }

    public BigDecimal decisionLinkUndervaluedS2() {
        return getDecimal("decision.link.undervalued_s2", new BigDecimal("6"));
    }

    public BigDecimal decisionLinkOvervaluedS3() {
        return getDecimal("decision.link.overvalued_s3", new BigDecimal("8"));
    }

    public int decisionConfluenceWindow() {
        return getInt("decision.confluence.window", 5);
    }

    public int decisionConfluenceMinStrategies() {
        return getInt("decision.confluence.min_strategies", 2);
    }

    public BigDecimal decisionExecutableScore() {
        return getDecimal("decision.executable.score", new BigDecimal("88"));
    }

    /**
     * 开仓门禁要求的最少上涨家数
     *
     * @return 最少上涨家数
     */
    public int decisionGateMinimumBreadthUp() {
        return getInt("decision.gate.minimum_breadth_up", 2000);
    }

    /**
     * 非主线标的获得热点确认所需的最少来源数
     *
     * @return 最少热点来源数
     */
    public int decisionGateMinimumHotSources() {
        return getInt("decision.gate.minimum_hot_sources", 2);
    }

    /**
     * 科技成长线可执行分数门槛
     *
     * @return 可执行分数门槛
     */
    public BigDecimal decisionGrowthExecutableScore() {
        return getDecimal("decision.growth.executable.score", new BigDecimal("82"));
    }

    /**
     * 科技成长线单票仓位上限
     *
     * @return 单票仓位上限
     */
    public BigDecimal decisionGrowthSingleLimit() {
        return getDecimal("decision.growth.single_limit", new BigDecimal("0.08"));
    }

    /**
     * 科技成长线单日新增仓位上限
     *
     * @return 单日新增仓位上限
     */
    public BigDecimal decisionGrowthTotalLimit() {
        return getDecimal("decision.growth.total_limit", new BigDecimal("0.20"));
    }
}
