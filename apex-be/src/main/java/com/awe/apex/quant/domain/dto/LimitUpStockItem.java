package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 连板天梯个股卡片
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LimitUpStockItem {

    /**
     * 证券代码
     */
    private String code;

    /**
     * 证券名称
     */
    private String name;

    /**
     * 连板数
     */
    private Integer lianban;

    /**
     * 涨跌幅%
     */
    private BigDecimal pctChg;

    /**
     * 最新价
     */
    private BigDecimal latestPrice;

    /**
     * 首次封板展示时间 如 09:25
     */
    private String firstSealTime;

    /**
     * 最后封板时间
     */
    private String lastSealTime;

    /**
     * 炸板次数
     */
    private Integer breakCount;

    /**
     * 封板资金（元）
     */
    private BigDecimal sealAmount;

    /**
     * 换手率%
     */
    private BigDecimal turnoverRate;

    /**
     * 题材/行业标签
     */
    private String theme;

    /**
     * 涨停统计
     */
    private String ztStats;

    /**
     * 是否一字板（竞价封死且未开板）
     */
    private Boolean yizi;

    /**
     * 是否断板终结（昨连板今日未晋级且未涨停，排除昨首板）
     */
    private Boolean failed;
}
