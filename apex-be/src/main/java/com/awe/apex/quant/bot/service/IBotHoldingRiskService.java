package com.awe.apex.quant.bot.service;

import com.awe.apex.quant.domain.dto.BotHoldingRiskResp;

/**
 * ClawBot 真实持仓风险服务。
 */
public interface IBotHoldingRiskService {

    /**
     * 使用真实持仓和最新行情生成风险摘要。
     *
     * @return 风险摘要
     */
    BotHoldingRiskResp analyze();
}
