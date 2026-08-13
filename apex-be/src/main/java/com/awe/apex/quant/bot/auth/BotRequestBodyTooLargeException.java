package com.awe.apex.quant.bot.auth;

import java.io.IOException;

/**
 * Bot API 请求体超过允许大小。
 */
public class BotRequestBodyTooLargeException extends IOException {

    /**
     * 创建请求体超限异常。
     *
     * @param message 错误信息
     */
    public BotRequestBodyTooLargeException(String message) {
        super(message);
    }
}
