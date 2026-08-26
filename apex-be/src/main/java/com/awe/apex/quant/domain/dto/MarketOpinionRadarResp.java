package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 首页市场观点雷达。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketOpinionRadarResp {

    /** 机构公开观点 */
    private List<MarketOpinionItemResp> institutionViews;

    /** 未映射到具体游资标签的龙虎榜活跃席位 */
    private List<MarketOpinionItemResp> activeSeats;

    /** 已映射的游资席位行为 */
    private List<MarketOpinionItemResp> traderSeatViews;

    /** 已授权大V公开观点 */
    private List<MarketOpinionItemResp> kolViews;

    /** 多机构同向结论 */
    private String consensus;

    /** 同一主题的相反评级 */
    private String divergence;

    /** 大V数据源状态 */
    private String kolSourceStatus;

    /** 公开账号白名单与核验状态 */
    private List<MarketOpinionSourceResp> kolSources;

    /** 最近同步时间 */
    private LocalDateTime snapshotTime;
}
