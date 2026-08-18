package com.awe.apex.common.config;

import cn.hutool.core.util.ArrayUtil;
import com.awe.apex.common.exception.SystemException;
import com.awe.apex.common.util.SpringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.Arrays;
import java.util.concurrent.Executor;

/**
 * 异步配置
 *
 * @author Awe
 * @since 2025/9/9 14:34
 */
@EnableAsync(proxyTargetClass = true)
@AutoConfiguration
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    /**
     * 自定义 @Async 注解使用系统线程池
     */
    @Override
    public Executor getAsyncExecutor() {
        return SpringUtils.getBean("scheduledExecutorService");
    }

    /**
     * 异步执行异常处理
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, objects) -> {
            String parameterText = ArrayUtil.isNotEmpty(objects) ? Arrays.toString(objects) : "[]";
            log.error("异步方法执行失败，方法={}，参数={}，原因={}",
                    method.getName(), parameterText, throwable.getMessage(), throwable);
            throw new SystemException("异步方法执行失败，方法=" + method.getName()
                    + "，参数=" + parameterText + "，原因=" + throwable.getMessage());
        };
    }

}
