package com.awe.apex.quant.market;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.awe.apex.common.util.JsonUtils;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.GlobalMarketIntradayResp;
import com.awe.apex.quant.domain.dto.IntradayKlineBar;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 全球市场日内 K 线客户端。
 */
@Slf4j
@Component
public class GlobalMarketIntradayClient {

    private static final int MAX_THUMBNAIL_BARS = 60;
    private static final String[] EAST_MONEY_HOSTS = {
            "https://push2delay.eastmoney.com/api/qt/stock/trends2/get",
            "https://push2.eastmoney.com/api/qt/stock/trends2/get"
    };

    /**
     * 查询美股指数或富时 A50 期指连续的日内 K 线。
     *
     * @param symbol 晨报行情代码
     * @return 日内 K 线；数据不可用时返回空
     */
    public GlobalMarketIntradayResp fetch(String symbol) {
        String secId = resolveSecId(symbol);
        if (StringUtils.isBlank(secId)) {
            return null;
        }
        Exception lastException = null;
        for (String host : EAST_MONEY_HOSTS) {
            String url = host
                    + "?fields1=f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f11,f12,f13"
                    + "&fields2=f51,f52,f53,f54,f55,f56,f57,f58"
                    + "&ut=fa5fd1943c7b386f172d6893dbfba10b"
                    + "&ndays=1&iscr=0&iscca=0"
                    + "&secid=" + secId
                    + "&_=" + System.currentTimeMillis();
            try (HttpResponse response = HttpRequest.get(url)
                    .timeout(8000)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://quote.eastmoney.com/")
                    .header("Accept", "*/*")
                    .execute()) {
                if (!response.isOk() || StringUtils.isBlank(response.body())) {
                    continue;
                }
                JsonNode root = JsonUtils.getObjectMapper().readTree(response.body());
                GlobalMarketIntradayResp intraday = parse(symbol, root);
                if (Objects.nonNull(intraday)) {
                    return intraday;
                }
            } catch (Exception ex) {
                lastException = ex;
            }
        }
        log.warn("全球市场日内K线读取失败，symbol={}，secId={}，原因={}", symbol, secId,
                Objects.nonNull(lastException) ? lastException.getMessage() : "行情源无有效数据");
        return null;
    }

    private String resolveSecId(String symbol) {
        if (StringUtils.isBlank(symbol)) {
            return null;
        }
        return switch (symbol.trim()) {
            case "usIXIC" -> "100.NDX";
            case "usDJI" -> "100.DJIA";
            case "usINX" -> "100.SPX";
            case "hf_CHA50CFD" -> "104.CN00Y";
            default -> null;
        };
    }

    private GlobalMarketIntradayResp parse(String symbol, JsonNode root) {
        JsonNode data = root.path("data");
        JsonNode trends = data.path("trends");
        if (!trends.isArray()) {
            return null;
        }
        List<IntradayKlineBar> rawBars = new ArrayList<>();
        for (JsonNode trend : trends) {
            String[] parts = trend.asText().split(",", -1);
            if (parts.length < 5) {
                continue;
            }
            BigDecimal openPrice = toDecimal(parts[1]);
            BigDecimal closePrice = toDecimal(parts[2]);
            BigDecimal highPrice = toDecimal(parts[3]);
            BigDecimal lowPrice = toDecimal(parts[4]);
            if (Objects.isNull(openPrice) || Objects.isNull(closePrice)
                    || Objects.isNull(highPrice) || Objects.isNull(lowPrice)) {
                continue;
            }
            rawBars.add(IntradayKlineBar.builder()
                    .datetime(parts[0].trim())
                    .openPrice(openPrice)
                    .closePrice(closePrice)
                    .highPrice(highPrice)
                    .lowPrice(lowPrice)
                    .build());
        }
        if (CollUtil.isEmpty(rawBars)) {
            return null;
        }

        int bucketSize = Math.max(1, (rawBars.size() + MAX_THUMBNAIL_BARS - 1) / MAX_THUMBNAIL_BARS);
        List<IntradayKlineBar> compactBars = new ArrayList<>();
        for (int startIndex = 0; startIndex < rawBars.size(); startIndex += bucketSize) {
            int endIndex = Math.min(startIndex + bucketSize, rawBars.size());
            IntradayKlineBar firstBar = rawBars.get(startIndex);
            IntradayKlineBar lastBar = rawBars.get(endIndex - 1);
            BigDecimal highPrice = firstBar.getHighPrice();
            BigDecimal lowPrice = firstBar.getLowPrice();
            for (int barIndex = startIndex + 1; barIndex < endIndex; barIndex++) {
                IntradayKlineBar currentBar = rawBars.get(barIndex);
                highPrice = highPrice.max(currentBar.getHighPrice());
                lowPrice = lowPrice.min(currentBar.getLowPrice());
            }
            compactBars.add(IntradayKlineBar.builder()
                    .datetime(firstBar.getDatetime())
                    .openPrice(firstBar.getOpenPrice())
                    .closePrice(lastBar.getClosePrice())
                    .highPrice(highPrice)
                    .lowPrice(lowPrice)
                    .build());
        }
        return GlobalMarketIntradayResp.builder()
                .previousClose(toDecimal(data.path("prePrice").asText(null)))
                .bars(compactBars)
                .build();
    }

    private BigDecimal toDecimal(String value) {
        if (StringUtils.isBlank(value) || "-".equals(value) || "--".equals(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
