package com.awe.apex.quant.service;

import com.awe.apex.common.api.PageResponse;
import com.awe.apex.quant.domain.dto.ScreenerMetaResp;
import com.awe.apex.quant.domain.dto.ScreenerReq;
import com.awe.apex.quant.domain.dto.WatchlistResp;

import java.util.List;

/**
 * 条件选股
 */
public interface IScreenerService {

    /**
     * 运行选股
     *
     * @param req 条件
     * @return 结果
     */
    List<WatchlistResp> run(ScreenerReq req);

    /**
     * 全市场 / 股票池数量摘要
     *
     * @return 规模信息
     */
    ScreenerMetaResp meta();

    /**
     * 分页浏览全市场股票
     *
     * @param keyword   代码或名称关键字
     * @param page      页码（从 1 起）
     * @param size      每页条数
     * @param excludeSt 是否排除 ST
     * @return 分页结果
     */
    PageResponse<WatchlistResp> listMarket(String keyword, Integer page, Integer size, Boolean excludeSt);
}
