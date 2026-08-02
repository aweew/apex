package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 数据质量概览
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataQualityResp {

    /**
     * 自选数量
     */
    private Integer watchlistCount;

    /**
     * 有行情快照数量
     */
    private Integer quotedCount;

    /**
     * 有足够K线数量
     */
    private Integer barsReadyCount;

    /**
     * 过期K线数量
     */
    private Integer barsStaleCount;

    /**
     * 无K线数量
     */
    private Integer barsEmptyCount;

    /**
     * 股票池数量
     */
    private Integer universeCount;

    /**
     * 近五日信号数量
     */
    private Integer recentSignalCount;

    /**
     * 建议动作
     */
    private String suggestion;

    /**
     * 无K线代码（最多20）
     */
    private List<String> emptyCodes;

    /**
     * 无行情代码（最多20）
     */
    private List<String> unquotedCodes;

    /**
     * 行情覆盖率
     */
    private BigDecimal quoteCoverage;

    /**
     * K线就绪率
     */
    private BigDecimal barsReadyCoverage;

    /**
     * SLA：GREEN / YELLOW / RED
     */
    private String slaLevel;

    /**
     * 市场数据源健康（指数/板块/涨停）
     */
    private List<DataSourceHealthItem> marketSources;

    /**
     * 市场数据综合等级
     */
    private String marketSlaLevel;
}
