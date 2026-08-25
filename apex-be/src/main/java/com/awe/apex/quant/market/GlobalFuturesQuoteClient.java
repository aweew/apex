package com.awe.apex.quant.market;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.OvernightMarketQuote;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 新浪全球期货行情客户端。
 */
@Slf4j
@Component
public class GlobalFuturesQuoteClient {

    private static final String SINA_QUOTE_URL = "https://hq.sinajs.cn/list=";

    /**
     * 读取指定全球期货合约的最新报价。
     *
     * @param symbol 新浪全球期货合约代码
     * @return 最新报价；数据不可用时返回空
     */
    public OvernightMarketQuote fetch(String symbol) {
        if (StringUtils.isBlank(symbol)) {
            return null;
        }
        try (HttpResponse response = HttpRequest.get(SINA_QUOTE_URL + symbol.trim())
                .timeout(15000)
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://finance.sina.com.cn/")
                .execute()) {
            if (!response.isOk() || StringUtils.isBlank(response.body())) {
                log.warn("全球期货行情请求失败，symbol={}, status={}", symbol, response.getStatus());
                return null;
            }
            return parse(symbol, new String(response.bodyBytes(), Charset.forName("GBK")));
        } catch (Exception ex) {
            log.warn("全球期货行情读取失败，symbol={}, reason={}", symbol, ex.getMessage());
            return null;
        }
    }

    private OvernightMarketQuote parse(String symbol, String response) {
        if (StringUtils.isBlank(response)) {
            return null;
        }
        int contentStart = response.indexOf('"');
        int contentEnd = response.lastIndexOf('"');
        if (contentStart < 0 || contentEnd <= contentStart) {
            return null;
        }
        String[] fields = response.substring(contentStart + 1, contentEnd).split(",", -1);
        if (fields.length < 4 || StringUtils.isBlank(fields[0])) {
            return null;
        }
        BigDecimal latestPrice = toDecimal(fields[1]);
        BigDecimal pctChg = toDecimal(fields[3]);
        if (Objects.isNull(latestPrice) || Objects.isNull(pctChg)) {
            return null;
        }
        return OvernightMarketQuote.builder()
                .symbol(symbol.trim())
                .name(fields[0].trim())
                .latestPrice(latestPrice)
                .pctChg(pctChg)
                .quoteTime(LocalDateTime.now())
                .build();
    }

    private BigDecimal toDecimal(String text) {
        if (StringUtils.isBlank(text) || "--".equals(text.trim())) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
