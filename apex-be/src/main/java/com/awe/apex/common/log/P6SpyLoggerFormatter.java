package com.awe.apex.common.log;

import com.p6spy.engine.spy.appender.MessageFormattingStrategy;

/**
 * P6Spy日志格式化
 *
 * @author Awe
 * @since 2024/6/17 09:20
 */
public class P6SpyLoggerFormatter implements MessageFormattingStrategy {

    @Override
    public String formatMessage(int connectionId, String now, long elapsed, String category, String prepared, String sql, String s4) {
        String costSql = String.format("SQL耗时：[%s ms] %s", elapsed, category);
        String executeSql = sql.replaceAll("[\\s\\n\\r\\t]+", " ");
        return String.format("  %s\n\n   %s", costSql, executeSql);
    }

}
