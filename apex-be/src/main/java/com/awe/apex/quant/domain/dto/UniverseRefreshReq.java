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
     * 代码列表，空则取自选
     */
    private List<String> codes;

    /**
     * 分组
     */
    private String groupName;

    /**
     * 宽松质量过滤：仅剔 ST / 日线不足，不因 PE/PB/市值硬剔除（观察池扩候选用）
     */
    private Boolean looseFilter;

    /**
     * 候选范围：WATCHLIST（默认，自选） / MARKET（有足够日线的全市场）
     */
    private String scope;

    /**
     * 日线统计截止日；空值保持原有最新数据行为
     */
    private LocalDate asOfDate;
}
