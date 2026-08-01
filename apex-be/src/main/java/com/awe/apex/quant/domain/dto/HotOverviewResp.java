package com.awe.apex.quant.domain.dto;

import com.awe.apex.quant.domain.entity.MarketHot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 热点总览
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotOverviewResp {

    /**
     * 各源最新快照时间
     */
    private Map<String, LocalDateTime> snapshotTimes;

    /**
     * 东财人气榜
     */
    private List<MarketHot> eastmoney;

    /**
     * 雪球关注热度
     */
    private List<MarketHot> xueqiu;

    /**
     * 百度热搜
     */
    private List<MarketHot> baidu;

    /**
     * 多源共振（≥2）
     */
    private List<HotConfluenceItem> confluence;

    /**
     * 说明
     */
    private String message;
}
