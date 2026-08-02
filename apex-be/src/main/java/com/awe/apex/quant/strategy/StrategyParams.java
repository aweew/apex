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
}
