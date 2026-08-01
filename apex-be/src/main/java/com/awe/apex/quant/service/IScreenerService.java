package com.awe.apex.quant.service;

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
}
