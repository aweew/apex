package com.awe.apex.quant.strategy;

/**
 * 择时策略
 */
public interface Strategy {

    /**
     * 策略ID
     *
     * @return ID
     */
    String strategyId();

    /**
     * 策略名称
     *
     * @return 名称
     */
    String strategyName();

    /**
     * 策略逻辑版本
     *
     * @return 逻辑版本
     */
    String logicVersion();

    /**
     * 评估最新一根 K 线是否产生信号
     *
     * @param code   证券代码
     * @param series 日线序列（升序）
     * @return 信号，无则 null
     */
    StrategySignalResult evaluate(String code, BarSeries series);

    /**
     * 是否应在 index 日开仓（回测用）
     *
     * @param series 序列
     * @param index  下标
     * @return true=开仓
     */
    boolean shouldEnter(BarSeries series, int index);

    /**
     * 是否应在 index 日平仓（回测用）
     *
     * @param series          序列
     * @param index           下标
     * @param entryIndex      开仓下标
     * @param entryBreakLow   突破策略用：突破日最低价，可空
     * @return true=平仓
     */
    boolean shouldExit(BarSeries series, int index, int entryIndex, java.math.BigDecimal entryBreakLow);
}
