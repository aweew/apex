package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.HotOverviewResp;
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
    Map<String, Object> refresh(String sources, Integer limit);
}
