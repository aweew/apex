package com.awe.apex.quant.market;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.OvernightMarketQuote;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 新浪全球期货行情客户端。
 */
@Slf4j
@Component
public class GlobalFuturesQuoteClient {

    private static final String SINA_QUOTE_URL = "http://hq.sinajs.cn/list=";
    private static final DateTimeFormatter QUOTE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
                log.warn("全球期货行情请求失败，合约代码={}，状态={}", symbol, response.getStatus());
                return null;
            }
            return parse(symbol, new String(response.bodyBytes(), Charset.forName("GBK")));
        } catch (Exception ex) {
            log.warn("全球期货行情读取失败，合约代码={}，原因={}", symbol, ex.getMessage());
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
        if (fields.length < 14 || StringUtils.isBlank(fields[13])) {
            return null;
        }
        BigDecimal latestPrice = toDecimal(fields[0]);
        BigDecimal previousSettlement = toDecimal(fields[7]);
        if (Objects.isNull(latestPrice) || Objects.isNull(previousSettlement)
                || previousSettlement.signum() == 0) {
            return null;
        }
        BigDecimal pctChg = latestPrice.subtract(previousSettlement)
                .multiply(BigDecimal.valueOf(100))
                .divide(previousSettlement, 2, RoundingMode.HALF_UP);
        LocalDateTime quoteTime = null;
        if (StringUtils.isNotBlank(fields[12]) && StringUtils.isNotBlank(fields[6])) {
            try {
                quoteTime = LocalDateTime.parse(fields[12].trim() + " " + fields[6].trim(), QUOTE_TIME_FORMATTER);
            } catch (Exception ex) {
                log.warn("全球期货行情时间解析失败，合约代码={}，日期={}，时间={}",
                        symbol, fields[12], fields[6]);
            }
        }
        if (Objects.isNull(quoteTime)) {
            return null;
        }
        return OvernightMarketQuote.builder()
                .symbol(symbol.trim())
                .name(fields[13].trim())
                .latestPrice(latestPrice)
                .pctChg(pctChg)
                .quoteTime(quoteTime)
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
