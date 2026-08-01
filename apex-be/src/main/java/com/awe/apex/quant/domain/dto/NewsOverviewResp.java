package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 新闻总览
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsOverviewResp {

    /**
     * 各源最新同步时间
     */
    private Map<String, LocalDateTime> snapshotTimes;

    /**
     * 各源条数
     */
    private Map<String, Integer> sourceCounts;

    /**
     * 新闻列表
     */
    private List<NewsItemResp> items;

    /**
     * 说明
     */
    private String message;
}
