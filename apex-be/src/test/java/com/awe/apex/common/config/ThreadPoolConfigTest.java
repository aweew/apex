package com.awe.apex.common.config;

import com.awe.apex.common.constant.Constants;
import com.awe.apex.common.config.properties.ThreadPoolProperties;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ThreadPoolConfigTest {

    @Test
    void syncJobExecutorShouldUseBoundedQueueAndRejectSaturatedSubmissions() {
        ThreadPoolConfig threadPoolConfig = new ThreadPoolConfig();
        ThreadPoolProperties properties = new ThreadPoolProperties();
        properties.setKeepAliveSeconds(30);
        ThreadPoolTaskExecutor executorService = threadPoolConfig.syncJobTaskExecutor(properties);
        executorService.initialize();

        try {
            assertEquals(2, executorService.getCorePoolSize());
            assertEquals(2, executorService.getMaxPoolSize());
            assertEquals(16, executorService.getThreadPoolExecutor().getQueue().remainingCapacity());
            assertInstanceOf(ThreadPoolExecutor.AbortPolicy.class,
                    executorService.getThreadPoolExecutor().getRejectedExecutionHandler());
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    void asyncTaskShouldRunOnce() throws Exception {
        ThreadPoolConfig threadPoolConfig = new ThreadPoolConfig();
        ThreadPoolProperties properties = new ThreadPoolProperties();
        properties.setQueueCapacity(10);
        properties.setKeepAliveSeconds(30);
        ThreadPoolTaskExecutor executorService = threadPoolConfig.threadPoolTaskExecutor(properties);
        executorService.initialize();
        AtomicInteger runCount = new AtomicInteger();
        MDC.put(Constants.TRACE_ID, "thread-pool-test");

        try {
            executorService.execute(runCount::incrementAndGet);
            executorService.getThreadPoolExecutor().shutdown();
            executorService.getThreadPoolExecutor().awaitTermination(3, TimeUnit.SECONDS);
        } finally {
            MDC.clear();
            executorService.getThreadPoolExecutor().shutdownNow();
        }

        assertEquals(1, runCount.get());
    }

    @Test
    void failingAsyncTaskShouldNotRetry() throws Exception {
        ThreadPoolConfig threadPoolConfig = new ThreadPoolConfig();
        ThreadPoolProperties properties = new ThreadPoolProperties();
        properties.setQueueCapacity(10);
        properties.setKeepAliveSeconds(30);
        ThreadPoolTaskExecutor executorService = threadPoolConfig.threadPoolTaskExecutor(properties);
        AtomicReference<Throwable> uncaughtException = new AtomicReference<>();
        executorService.setThreadFactory(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setUncaughtExceptionHandler((currentThread, exception) -> uncaughtException.set(exception));
            return thread;
        });
        executorService.initialize();
        AtomicInteger runCount = new AtomicInteger();
        MDC.put(Constants.TRACE_ID, "thread-pool-error-test");

        try {
            executorService.execute(() -> {
                runCount.incrementAndGet();
                throw new IllegalStateException("expected test exception");
            });
            executorService.getThreadPoolExecutor().shutdown();
            executorService.getThreadPoolExecutor().awaitTermination(3, TimeUnit.SECONDS);
        } finally {
            MDC.clear();
            executorService.getThreadPoolExecutor().shutdownNow();
        }

        assertEquals(1, runCount.get());
        assertInstanceOf(IllegalStateException.class, uncaughtException.get());
    }
}
