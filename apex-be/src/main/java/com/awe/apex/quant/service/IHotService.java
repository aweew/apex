package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.HotConfluenceItem;
import com.awe.apex.quant.domain.dto.HotOverviewResp;
import com.awe.apex.quant.domain.dto.HotRefreshResp;
import com.awe.apex.quant.domain.entity.MarketHot;

import java.util.List;
import java.util.Map;

/**
 * 多平台热点
 */
public interface IHotService {

    /**
     * 热点总览（各源最新快照 + 共振）
     *
     * @param limit 每源条数
     * @return 总览
     */
    HotOverviewResp overview(Integer limit);

    /**
     * 按来源查询最新快照
     *
     * @param source 来源
     * @param limit  条数
     * @return 列表
     */
    List<MarketHot> listBySource(String source, Integer limit);

    /**
     * 调用脚本刷新热点
     *
     * @param sources 来源，逗号分隔可空
     * @param limit   每源条数
     * @return 结果
     */
    HotRefreshResp refresh(String sources, Integer limit);

    /**
     * 最新多源共振（code -> 条目），供决策/今日关注加分
     *
     * @param limit 每源条数
     * @return 共振映射
     */
    Map<String, HotConfluenceItem> confluenceMap(Integer limit);
}
