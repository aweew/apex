package com.awe.apex.quant.market;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.awe.apex.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 全A截面实时统计：平均股价、平均涨幅、涨幅中位数、强弱家数
 */
@Slf4j
@Component
public class MarketCrossSectionClient {

    private static final String CLIST_HOST = "https://push2delay.eastmoney.com/api/qt/clist/get";
    /** 沪深京 A 股（含创业板/科创板/北证） */
    private static final String FS_HSJ_A =
            "m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23,m:0+t:81+s:2048";
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 70;
    private static final int PARALLELISM = 12;
    private static final long PAGE_TIMEOUT_MS = 4500L;

    /**
     * 拉取全A截面并汇总
     *
     * @return 截面统计，失败返回 null
     */
    public CrossSectionStats fetchHsjAStats() {
        PageResult first = fetchPage(1);
        if (Objects.isNull(first) || first.total <= 0) {
            return null;
        }
        int pages = Math.min(MAX_PAGES, (first.total + PAGE_SIZE - 1) / PAGE_SIZE);
        List<BigDecimal> prices = new ArrayList<>(Math.min(first.total, pages * PAGE_SIZE));
        List<BigDecimal> pcts = new ArrayList<>(Math.min(first.total, pages * PAGE_SIZE));
        int strongUp = 0;
        int strongDown = 0;
        strongUp += first.strongUp;
        strongDown += first.strongDown;
        prices.addAll(first.prices);
        pcts.addAll(first.pcts);

        if (pages <= 1) {
            return toStats(prices, pcts, strongUp, strongDown);
        }

        ExecutorService pool = Executors.newFixedThreadPool(PARALLELISM);
        try {
            List<CompletableFuture<PageResult>> futures = new ArrayList<>();
            for (int pn = 2; pn <= pages; pn++) {
                final int pageNo = pn;
                futures.add(CompletableFuture.supplyAsync(() -> fetchPage(pageNo), pool));
            }
            for (CompletableFuture<PageResult> future : futures) {
                try {
                    PageResult page = future.get(PAGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    if (Objects.isNull(page)) {
                        continue;
                    }
                    prices.addAll(page.prices);
                    pcts.addAll(page.pcts);
                    strongUp += page.strongUp;
                    strongDown += page.strongDown;
                } catch (Exception ex) {
                    log.debug("东财 clist 分页超时/失败: {}", ex.getMessage());
                }
            }
        } finally {
            pool.shutdownNow();
        }
        return toStats(prices, pcts, strongUp, strongDown);
    }

    private CrossSectionStats toStats(List<BigDecimal> prices, List<BigDecimal> pcts,
                                      int strongUp, int strongDown) {
        if (prices.isEmpty() && pcts.isEmpty()) {
            return null;
        }
        CrossSectionStats stats = new CrossSectionStats();
        stats.avgPrice = MarketBriefingMath.average(prices, 2);
        stats.avgPct = MarketBriefingMath.average(pcts, 2);
        stats.medianPct = MarketBriefingMath.median(pcts, 2);
        stats.sampleSize = Math.max(prices.size(), pcts.size());
        stats.strongUpCount = strongUp;
        stats.strongDownCount = strongDown;
        return stats;
    }

    private PageResult fetchPage(int pageNo) {
        String url = CLIST_HOST
                + "?pn=" + pageNo
                + "&pz=" + PAGE_SIZE
                + "&po=1&np=1&fltt=2&invt=2&fid=f12"
                + "&fs=" + FS_HSJ_A
                + "&fields=f2,f3";
        try (HttpResponse response = HttpRequest.get(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://quote.eastmoney.com/")
                .header("Accept", "application/json,text/plain,*/*")
                .timeout((int) PAGE_TIMEOUT_MS)
                .execute()) {
            if (!response.isOk() || StringUtils.isBlank(response.body())) {
                return null;
            }
            JSONObject data = JSONUtil.parseObj(response.body()).getJSONObject("data");
            if (Objects.isNull(data)) {
                return null;
            }
            PageResult result = new PageResult();
            result.total = data.getInt("total", 0);
            JSONArray diff = data.getJSONArray("diff");
            if (Objects.isNull(diff)) {
                return result;
            }
            BigDecimal five = new BigDecimal("5");
            BigDecimal negFive = new BigDecimal("-5");
            for (int i = 0; i < diff.size(); i++) {
                JSONObject row = diff.getJSONObject(i);
                if (Objects.isNull(row)) {
                    continue;
                }
                BigDecimal price = toPositiveDecimal(row.get("f2"));
                BigDecimal pct = toDecimal(row.get("f3"));
                if (Objects.nonNull(price)) {
                    result.prices.add(price);
                }
                if (Objects.nonNull(pct)) {
                    result.pcts.add(pct);
                    if (pct.compareTo(five) >= 0) {
                        result.strongUp++;
                    } else if (pct.compareTo(negFive) <= 0) {
                        result.strongDown++;
                    }
                }
            }
            return result;
        } catch (Exception ex) {
            log.debug("东财 clist 拉取失败，页码={}，原因={}", pageNo, ex.getMessage());
            return null;
        }
    }

    private BigDecimal toPositiveDecimal(Object raw) {
        BigDecimal value = toDecimal(raw);
        if (Objects.isNull(value) || value.signum() <= 0) {
            return null;
        }
        return value;
    }

    private BigDecimal toDecimal(Object raw) {
        if (Objects.isNull(raw) || "-".equals(String.valueOf(raw)) || "".equals(String.valueOf(raw))) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(raw)).setScale(4, RoundingMode.HALF_UP);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 截面汇总
     */
    public static final class CrossSectionStats {
        /** 平均股价 */
        public BigDecimal avgPrice;
        /** 平均涨幅% */
        public BigDecimal avgPct;
        /** 涨幅中位数% */
        public BigDecimal medianPct;
        /** 样本数 */
        public int sampleSize;
        /** 涨超5% */
        public int strongUpCount;
        /** 跌超5% */
        public int strongDownCount;
    }

    private static final class PageResult {
        private int total;
        private final List<BigDecimal> prices = new ArrayList<>();
        private final List<BigDecimal> pcts = new ArrayList<>();
        private int strongUp;
        private int strongDown;
    }
}
