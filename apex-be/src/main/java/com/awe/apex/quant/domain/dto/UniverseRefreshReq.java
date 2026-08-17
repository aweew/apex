package com.awe.apex.quant.domain.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 股票池刷新请求
 */
@Data
public class UniverseRefreshReq {

    /**
     * 手工代码，仅用于识别并拒绝非共享发布请求
     */
    private List<String> codes;

    /**
     * 自选分组，仅用于识别并拒绝非共享发布请求
     */
    private String groupName;

    /**
     * 宽松质量过滤：仅剔 ST / 日线不足，不因 PE/PB/市值硬剔除（观察池扩候选用）
     */
    private Boolean looseFilter;

    /**
     * 候选范围：仅支持 MARKET（有足够日线的全市场）
     */
    private String scope;

    /**
     * 日线统计截止日；空值保持原有最新数据行为
     */
    private LocalDate asOfDate;

    /**
     * 是否纳入北交所（京市）。
     * null=不过滤（信号页等）；false=剔除；true=保留。智能决策默认传 false。
     */
    private Boolean includeBj;
}
