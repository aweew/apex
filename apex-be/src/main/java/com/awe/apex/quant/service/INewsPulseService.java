package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.NewsPulseResp;

/**
 * 今日消息面
 */
public interface INewsPulseService {

    /**
     * 构建今日消息面
     *
     * @param cardLimit 卡片条数
     * @param forceLlm  强制刷新大模型摘要
     * @return 消息面
     */
    NewsPulseResp pulse(Integer cardLimit, boolean forceLlm);
}
