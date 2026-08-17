package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.ScreenerStrategyRunReq;
import com.awe.apex.quant.domain.dto.ScreenerStrategyRunResp;

/**
 * 选股策略执行服务
 */
public interface IScreenerStrategyExecutionService {

    /**
     * 按单策略 AND 规则运行选股。
     *
     * @param req 运行请求
     * @return 选股结果与数据状态
     */
    ScreenerStrategyRunResp run(ScreenerStrategyRunReq req);
}
