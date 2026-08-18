package com.awe.apex.common.log;

import com.p6spy.engine.logging.Category;
import com.p6spy.engine.spy.appender.FormattedLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * P6Spy for slf4j
 *
 * @author Awe
 * @since 2024/6/17 15:00
 */
@Component
@Slf4j
public class P6SpySlf4JLogger extends FormattedLogger {

    private static final List<String> filterList = List.of(

    );

    public P6SpySlf4JLogger() {
    }

    public void logException(Exception e) {
        log.info("数据库日志异常", e);
    }

    public void logText(String text) {
        log.info("数据库日志：{}", text);
    }

    public void logSQL(int connectionId, String now, long elapsed, Category category, String prepared, String sql, String url) {

        if (filterList.stream().anyMatch(sql::contains)) {
            return;
        }
        String msg = this.strategy.formatMessage(connectionId, now, elapsed, category.toString(), prepared, sql, url);
        if (Category.ERROR.equals(category)) {
            log.error("数据库错误日志：{}", msg);
        } else if (Category.WARN.equals(category)) {
            log.warn("数据库警告日志：{}", msg);
        } else if (Category.DEBUG.equals(category)) {
            log.debug("数据库调试日志：{}", msg);
        } else if (Category.STATEMENT.equals(category)) {
            log.debug("数据库语句日志：{}", msg);
        } else {
            log.info("数据库普通日志：{}", msg);
        }

    }

    public boolean isCategoryEnabled(Category category) {
        if (Category.ERROR.equals(category)) {
            return log.isErrorEnabled();
        } else if (Category.WARN.equals(category)) {
            return log.isWarnEnabled();
        } else {
            return Category.DEBUG.equals(category) ? log.isDebugEnabled() : log.isInfoEnabled();
        }
    }

}
