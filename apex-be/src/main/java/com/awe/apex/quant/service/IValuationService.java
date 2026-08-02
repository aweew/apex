package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.ValuationBriefResp;
import com.awe.apex.quant.domain.dto.ValuationResp;
import com.awe.apex.quant.domain.dto.ValuationScreenItemResp;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 综合估值评估
 */
public interface IValuationService {

    /**
     * 个股完整估值（行业相对 / PEG / 简化内在价值 / 质量）
     *
     * @param code 证券代码
     * @return 估值结论
     */
    ValuationResp evaluate(String code);

    /**
     * 轻量估值摘要（决策加减分用）
     *
     * @param code 证券代码
     * @return 摘要
     */
    ValuationBriefResp brief(String code);

    /**
     * 批量轻量估值（同行业统计复用）
     *
     * @param codes 代码列表
     * @return code -> 摘要
     */
    Map<String, ValuationBriefResp> briefBatch(Collection<String> codes);

    /**
     * 估值筛选：按综合分排序
     *
     * @param universe market / watchlist / observe
     * @param limit    条数
     * @param level    可选过滤：UNDERVALUED / SLIGHTLY_CHEAP / FAIR ...
     * @return 列表
     */
    List<ValuationScreenItemResp> screen(String universe, Integer limit, String level);
}
