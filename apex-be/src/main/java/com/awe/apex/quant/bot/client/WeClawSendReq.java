package com.awe.apex.quant.bot.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WeClaw 文本消息请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeClawSendReq {

    /**
     * 微信收件人标识。
     */
    private String to;

    /**
     * 消息文本。
     */
    private String text;
}
