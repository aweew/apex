package com.awe.apex.quant.bot.service;

import com.awe.apex.quant.domain.dto.BotToolReq;
import com.awe.apex.quant.domain.dto.BotToolResp;

/**
 * ClawBot 结构化工具服务。
 */
public interface IBotToolService {

    /**
     * 执行受控 Bot 工具。
     *
     * @param request 工具请求
     * @return 工具响应
     */
    BotToolResp execute(BotToolReq request);
}
