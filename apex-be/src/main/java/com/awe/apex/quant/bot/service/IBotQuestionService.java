package com.awe.apex.quant.bot.service;

import com.awe.apex.quant.domain.dto.BotAskReq;
import com.awe.apex.quant.domain.dto.BotAskResp;

/**
 * ClawBot 股票问答服务。
 */
public interface IBotQuestionService {

    /**
     * 回答股票相关问题。
     *
     * @param request 问答请求
     * @return 可直接发送到微信的回答
     */
    BotAskResp ask(BotAskReq request);
}
