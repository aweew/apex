package com.awe.apex.common.config;

import com.awe.apex.common.config.properties.ThreadPoolProperties;
import com.awe.apex.common.util.ThreadUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.*;

/**
 * 线程池配置
 *
 * @author Awe
 * @since 2025/9/9 14:34
 **/
@AutoConfiguration
@EnableConfigurationProperties(ThreadPoolProperties.class)
public class ThreadPoolConfig {

    /**
     * 核心线程数 = cpu 核心数 + 1
     */
    private final int core = Runtime.getRuntime().availableProcessors() + 1;

    @Bean(name = "threadPoolTaskExecutor")
    @ConditionalOnProperty(prefix = "thread-pool", name = "enabled", havingValue = "true")
    public ThreadPoolTaskExecutor threadPoolTaskExecutor(ThreadPoolProperties threadPoolProperties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(core * 2);
        executor.setQueueCapacity(threadPoolProperties.getQueueCapacity());
        executor.setKeepAliveSeconds(threadPoolProperties.getKeepAliveSeconds());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(MdcTaskDecorator::new);
        executor.setThreadNamePrefix("async-");
        return executor;
    }

    /**
     * 执行周期性或定时任务
     */
    @Bean(name = "scheduledExecutorService")
    protected ScheduledExecutorService scheduledExecutorService() {
        return new ScheduledThreadPoolExecutor(core,
                new BasicThreadFactory.Builder().namingPattern("schedule-pool-%d").daemon(true).build(),
                new ThreadPoolExecutor.CallerRunsPolicy()) {
            @Override
            protected void afterExecute(Runnable r, Throwable t) {
                super.afterExecute(r, t);
                ThreadUtils.printException(r, t);
            }

            @Override
            public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
                return super.schedule(new MdcTaskDecorator(command), delay, unit);
            }

            @Override
            public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
                return super.scheduleAtFixedRate(new MdcTaskDecorator(command), initialDelay, period, unit);
            }

            @Override
            public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
                return super.scheduleWithFixedDelay(new MdcTaskDecorator(command), initialDelay, delay, unit);
            }

            @Override
            public void execute(Runnable command) {
                super.execute(new MdcTaskDecorator(command));
            }
        };
    }

    // 自定义的Runnable装饰器，用于设置MDC
    private static class MdcTaskDecorator implements Runnable {

        private final Runnable delegate;

        private final Map<String, String> logContextMap;

        public MdcTaskDecorator(Runnable delegate) {
            this.delegate = delegate;
            this.logContextMap = MDC.getCopyOfContextMap();
        }

        @Override
        public void run() {
            Map<String, String> previousContextMap = MDC.getCopyOfContextMap();
            if (this.logContextMap != null) {
                MDC.setContextMap(this.logContextMap);
            } else {
                MDC.clear();
            }

            try {
                delegate.run();
            } finally {
                if (previousContextMap != null) {
                    MDC.setContextMap(previousContextMap);
                } else {
                    MDC.clear();
                }
            }
        }

    }

}
