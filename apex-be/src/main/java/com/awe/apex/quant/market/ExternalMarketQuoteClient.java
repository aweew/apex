package com.awe.apex.quant.market;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.awe.apex.common.util.JsonUtils;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.ExternalMarketItemResp;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 盘前外围市场行情客户端。
 */
@Slf4j
@Component
public class ExternalMarketQuoteClient {

    private static final String QUOTE_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";
    private static final String TENCENT_FUTURES_URL = "https://qt.gtimg.cn/q=hf_GC,hf_CL";
    private static final String EASTMONEY_OFFSHORE_RENMINBI_URL = "http://push2.eastmoney.com/api/qt/ulist.np/get"
            + "?fltt=2&secids=133.USDCNH&fields=f12,f14,f2,f3,f124,f152";
    private static final String SOURCE = "Yahoo Finance";
    private static final String TENCENT_SOURCE = "Tencent Finance";
    private static final String EASTMONEY_SOURCE = "Eastmoney";
    private static final String UNAVAILABLE_IMPACT = "当前未获取报价，暂不据此判断 A 股影响。";

    /**
     * 拉取外围市场观察指标。
     *
     * @return 固定五项指标列表，单项失败不影响其他指标
     */
    public List<ExternalMarketItemResp> fetch() {
        List<ExternalMarketItemResp> items = new ArrayList<>();
        List<CompletableFuture<ExternalMarketItemResp>> futures = new ArrayList<>();
        ExternalMarketIndicatorEnum[] indicators = ExternalMarketIndicatorEnum.values();
        for (ExternalMarketIndicatorEnum indicator : indicators) {
            futures.add(CompletableFuture.supplyAsync(() -> fetch(indicator)));
        }
        for (int index = 0; index < indicators.length; index++) {
            ExternalMarketItemResp item = futures.get(index).join();
            items.add(Objects.nonNull(item) ? item : buildUnavailableItem(indicators[index]));
        }
        return items;
    }

    private ExternalMarketItemResp fetch(ExternalMarketIndicatorEnum indicator) {
        String url = QUOTE_URL + URLEncoder.encode(indicator.getSourceSymbol(), StandardCharsets.UTF_8)
                + "?range=5d&interval=1d";
        try (HttpResponse response = HttpRequest.get(url)
                .timeout(8000)
                .header("User-Agent", "Mozilla/5.0")
                .execute()) {
            if (!response.isOk() || StringUtils.isBlank(response.body())) {
                log.warn("外围市场行情请求失败，indicator={}, status={}", indicator.getCode(), response.getStatus());
                return fetchFallback(indicator);
            }
            ExternalMarketItemResp item = parse(indicator, response.body());
            return Objects.nonNull(item) ? item : fetchFallback(indicator);
        } catch (Exception ex) {
            log.warn("外围市场行情读取失败，indicator={}, reason={}", indicator.getCode(), ex.getMessage());
            return fetchFallback(indicator);
        }
    }

    private ExternalMarketItemResp fetchFallback(ExternalMarketIndicatorEnum indicator) {
        if (ExternalMarketIndicatorEnum.GOLD == indicator || ExternalMarketIndicatorEnum.CRUDE_OIL == indicator) {
            return fetchTencentFutures(indicator);
        }
        if (ExternalMarketIndicatorEnum.OFFSHORE_RENMINBI == indicator) {
            return fetchEastmoneyOffshoreRenminbi(indicator);
        }
        return null;
    }

    private ExternalMarketItemResp fetchTencentFutures(ExternalMarketIndicatorEnum indicator) {
        try (HttpResponse response = HttpRequest.get(TENCENT_FUTURES_URL)
                .timeout(8000)
                .header("User-Agent", "Mozilla/5.0")
                .execute()) {
            if (!response.isOk() || StringUtils.isBlank(response.body())) {
                log.warn("腾讯期货备用行情请求失败，indicator={}, status={}", indicator.getCode(), response.getStatus());
                return null;
            }
            return parseTencentFutures(indicator, response.body());
        } catch (Exception ex) {
            log.warn("腾讯期货备用行情读取失败，indicator={}, reason={}", indicator.getCode(), ex.getMessage());
            return null;
        }
    }

    private ExternalMarketItemResp fetchEastmoneyOffshoreRenminbi(ExternalMarketIndicatorEnum indicator) {
        try (HttpResponse response = HttpRequest.get(EASTMONEY_OFFSHORE_RENMINBI_URL)
                .timeout(8000)
                .header("User-Agent", "Mozilla/5.0")
                .execute()) {
            if (!response.isOk() || StringUtils.isBlank(response.body())) {
                log.warn("东方财富离岸人民币备用行情请求失败，indicator={}, status={}", indicator.getCode(), response.getStatus());
                return null;
            }
            return parseEastmoneyOffshoreRenminbi(indicator, response.body());
        } catch (Exception ex) {
            log.warn("东方财富离岸人民币备用行情读取失败，indicator={}, reason={}", indicator.getCode(), ex.getMessage());
            return null;
        }
    }

    ExternalMarketItemResp parse(ExternalMarketIndicatorEnum indicator, String response) throws Exception {
        JsonNode result = JsonUtils.getObjectMapper().readTree(response)
                .path("chart").path("result");
        if (!result.isArray() || result.isEmpty()) {
            return null;
        }
        JsonNode quote = result.get(0);
        JsonNode metadata = quote.path("meta");
        BigDecimal latestPrice = decimal(metadata.path("regularMarketPrice"));
        BigDecimal previousClose = decimal(metadata.path("chartPreviousClose"));
        if (Objects.isNull(latestPrice)) {
            return null;
        }
        return buildAvailableItem(indicator, latestPrice, calculatePctChg(latestPrice, previousClose),
                quoteTime(metadata.path("regularMarketTime")), SOURCE);
    }

    ExternalMarketItemResp parseTencentFutures(ExternalMarketIndicatorEnum indicator, String response) {
        if (ExternalMarketIndicatorEnum.GOLD != indicator && ExternalMarketIndicatorEnum.CRUDE_OIL != indicator) {
            return null;
        }
        String sourceSymbol = ExternalMarketIndicatorEnum.GOLD == indicator ? "hf_GC" : "hf_CL";
        String prefix = "v_" + sourceSymbol + "=\"";
        int start = response.indexOf(prefix);
        if (start < 0) {
            return null;
        }
        int end = response.indexOf("\"", start + prefix.length());
        if (end < 0) {
            return null;
        }
        String[] fields = response.substring(start + prefix.length(), end).split(",");
        if (fields.length < 13) {
            return null;
        }
        BigDecimal latestPrice = decimal(fields[0]);
        if (Objects.isNull(latestPrice)) {
            return null;
        }
        LocalDateTime quoteTime = null;
        if (StringUtils.isNotBlank(fields[12]) && StringUtils.isNotBlank(fields[6])) {
            try {
                quoteTime = LocalDateTime.parse(fields[12] + " " + fields[6],
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (Exception ex) {
                log.warn("腾讯期货行情时间解析失败，indicator={}, date={}, time={}", indicator.getCode(), fields[12], fields[6]);
            }
        }
        return buildAvailableItem(indicator, latestPrice, decimal(fields[1]), quoteTime, TENCENT_SOURCE);
    }

    ExternalMarketItemResp parseEastmoneyOffshoreRenminbi(ExternalMarketIndicatorEnum indicator, String response)
            throws Exception {
        JsonNode item = JsonUtils.getObjectMapper().readTree(response).path("data").path("diff");
        if (!item.isArray() || item.isEmpty()) {
            return null;
        }
        JsonNode quote = item.get(0);
        BigDecimal latestPrice = decimal(quote.path("f2"));
        if (Objects.isNull(latestPrice)) {
            return null;
        }
        return buildAvailableItem(indicator, latestPrice, decimal(quote.path("f3")),
                quoteTime(quote.path("f124")), EASTMONEY_SOURCE);
    }

    private ExternalMarketItemResp buildAvailableItem(ExternalMarketIndicatorEnum indicator, BigDecimal latestPrice,
                                                      BigDecimal pctChg, LocalDateTime quoteTime, String source) {
        return ExternalMarketItemResp.builder()
                .code(indicator.getCode())
                .name(indicator.getDesc())
                .available(true)
                .latestPrice(latestPrice)
                .pctChg(pctChg)
                .quoteTime(quoteTime)
                .source(source)
                .aShareImpact(buildImpact(indicator, pctChg))
                .build();
    }

    private ExternalMarketItemResp buildUnavailableItem(ExternalMarketIndicatorEnum indicator) {
        return ExternalMarketItemResp.builder()
                .code(indicator.getCode())
                .name(indicator.getDesc())
                .available(false)
                .aShareImpact(UNAVAILABLE_IMPACT)
                .build();
    }

    private String buildImpact(ExternalMarketIndicatorEnum indicator, BigDecimal pctChg) {
        if (Objects.isNull(pctChg)) {
            return "已获取报价，但涨跌幅暂未获取，暂不据此判断 A 股影响。";
        }
        if (pctChg.signum() > 0) {
            return indicator.getRiseImpact();
        }
        if (pctChg.signum() < 0) {
            return indicator.getFallImpact();
        }
        return indicator.getDesc() + "基本持平，对 A 股的短线影响通常有限，需结合其他指标观察。";
    }

    private BigDecimal calculatePctChg(BigDecimal latestPrice, BigDecimal previousClose) {
        if (Objects.isNull(previousClose) || previousClose.signum() == 0) {
            return null;
        }
        return latestPrice.subtract(previousClose).multiply(new BigDecimal("100"))
                .divide(previousClose, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal(JsonNode value) {
        if (Objects.isNull(value) || value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        if (StringUtils.isBlank(text) || "--".equals(text)) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal decimal(String value) {
        if (StringUtils.isBlank(value) || "--".equals(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDateTime quoteTime(JsonNode value) {
        if (Objects.isNull(value) || !value.canConvertToLong() || value.asLong() <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(value.asLong()), ZoneId.systemDefault());
    }
}
