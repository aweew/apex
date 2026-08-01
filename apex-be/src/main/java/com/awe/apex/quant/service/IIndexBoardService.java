package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.IndexBoardResp;
import com.awe.apex.quant.domain.dto.IndexRefreshResp;
import com.awe.apex.quant.domain.entity.IndexBar;

import java.util.List;

/**
 * 大盘指数看板
 */
public interface IIndexBoardService {

    /**
     * 分市场看板
     *
     * @param sparkDays 迷你走势天数
     * @return 看板
     */
    IndexBoardResp board(Integer sparkDays);

    /**
     * 指数历史日线
     *
     * @param code  内部代码
     * @param limit 条数
     * @return 日线（升序）
     */
    List<IndexBar> bars(String code, Integer limit);

    /**
     * 刷新指数（调用同步脚本）
     *
     * @param start 起始 yyyyMMdd，可空
     * @return 结果
     */
    IndexRefreshResp refresh(String start);
}
