package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.NewsItemResp;
import com.awe.apex.quant.domain.dto.NewsOverviewResp;
import com.awe.apex.quant.domain.dto.NewsRefreshResp;

import java.util.List;

/**
 * 新闻资讯服务
 */
public interface INewsService {

    /**
     * 新闻总览
     *
     * @param source 来源，可空=全部
     * @param limit  条数
     * @param keyword 关键词，可空
     * @return 总览
     */
    NewsOverviewResp overview(String source, Integer limit, String keyword);

    /**
     * 按来源列表
     *
     * @param source 来源
     * @param limit  条数
     * @return 列表
     */
    List<NewsItemResp> listBySource(String source, Integer limit);

    /**
     * 调用脚本刷新
     *
     * @param sources 来源
     * @param limit   每源条数
     * @return 结果
     */
    NewsRefreshResp refresh(String sources, Integer limit);
}
