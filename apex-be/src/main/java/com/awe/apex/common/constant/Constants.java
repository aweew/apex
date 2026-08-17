package com.awe.apex.common.constant;

/**
 * 通用常量信息
 *
 * @author Awe
 * @since 2025/9/9 14:47
 */
public interface Constants {

    /**
     * 日志跟踪标识
     */
    String TRACE_ID = "traceId";

    /**
     * 日志中的脱敏手机号
     */
    String PHONE = "phone";

    /**
     * 请求日志级别标记
     */
    String REQUEST_LOG_LEVEL = Constants.class.getName() + ".requestLogLevel";

    /**
     * 请求警告日志级别
     */
    String LOG_LEVEL_WARN = "WARN";

    /**
     * 请求错误日志级别
     */
    String LOG_LEVEL_ERROR = "ERROR";

    /**
     * UTF-8 字符集
     */
    String UTF8 = "UTF-8";

    /**
     * 用户标识
     */
    String USER_ID = "userId";

}
