package com.awe.apex.quant.screener;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.bo.ScreenerIntradayCacheEntry;
import com.awe.apex.quant.domain.dto.ScreenerIntradayFetchResp;
import com.awe.apex.quant.domain.dto.StockIntradayResp;
import com.awe.apex.quant.market.IntradayQuoteClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 选股分时限流并发与短时缓存服务
 */
@Component
public class ScreenerIntradayReviewService {

    @Resource
    private IntradayQuoteClient intradayQuoteClient;

    @Value("${apex.screener.intraday-concurrency:4}")
    private Integer concurrency;

    @Value("${apex.screener.intraday-cache-seconds:45}")
    private Integer cacheSeconds;

    private final ConcurrentHashMap<String, ScreenerIntradayCacheEntry> cache = new ConcurrentHashMap<>();

    private ExecutorService executorService;

    /**
     * 初始化分时复核线程池。
     */
    @PostConstruct
    public void init() {
        int poolSize = Objects.nonNull(concurrency) ? Math.max(1, Math.min(concurrency, 8)) : 4;
        executorService = Executors.newFixedThreadPool(poolSize, runnable -> {
            Thread thread = new Thread(runnable, "screener-intraday");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 并发拉取候选股票分时。
     *
     * @param codes 证券代码
     * @return 每只股票的分时拉取结果
     */
    public List<ScreenerIntradayFetchResp> review(List<String> codes) {
        if (CollUtil.isEmpty(codes)) {
            return List.of();
        }
        if (Objects.isNull(executorService)) {
            init();
        }
        List<CompletableFuture<ScreenerIntradayFetchResp>> futures = new ArrayList<>();
        for (String code : codes) {
            if (StringUtils.isBlank(code)) {
                continue;
            }
            futures.add(CompletableFuture.supplyAsync(() -> fetchOne(code), executorService));
        }
        List<ScreenerIntradayFetchResp> results = new ArrayList<>();
        for (CompletableFuture<ScreenerIntradayFetchResp> future : futures) {
            results.add(future.join());
        }
        return results;
    }

    /**
     * 关闭分时复核线程池。
     */
    @PreDestroy
    public void destroy() {
        if (Objects.nonNull(executorService)) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                executorService.shutdownNow();
            }
        }
    }

    private ScreenerIntradayFetchResp fetchOne(String code) {
        long now = System.currentTimeMillis();
        ScreenerIntradayCacheEntry cached = cache.get(code);
        if (Objects.nonNull(cached) && Objects.nonNull(cached.getExpiresAt())
                && cached.getExpiresAt() > now && Objects.nonNull(cached.getResponse())) {
            return ScreenerIntradayFetchResp.builder()
                    .code(code)
                    .intraday(cached.getResponse())
                    .cached(true)
                    .build();
        }
        try {
            StockIntradayResp response = intradayQuoteClient.fetch(code);
            int ttl = Objects.nonNull(cacheSeconds) ? Math.max(5, cacheSeconds) : 45;
            cache.put(code, ScreenerIntradayCacheEntry.builder()
                    .response(response)
                    .expiresAt(now + ttl * 1000L)
                    .build());
            return ScreenerIntradayFetchResp.builder()
                    .code(code)
                    .intraday(response)
                    .cached(false)
                    .build();
        } catch (Exception ex) {
            return ScreenerIntradayFetchResp.builder()
                    .code(code)
                    .error(StringUtils.isNotBlank(ex.getMessage()) ? ex.getMessage() : "分时拉取失败")
                    .cached(false)
                    .build();
        }
    }
}
