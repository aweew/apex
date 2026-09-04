package com.awe.apex.quant.market;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.OvernightMarketQuote;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 腾讯盘前跨市场行情客户端。
 */
@Slf4j
@Component
public class UsMarketQuoteClient {

    private static final DateTimeFormatter[] QUOTE_TIME_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
    };

    /**
     * 批量读取美股、亚太指数及个股最新报价。
     *
     * @param symbols 腾讯行情符号
     * @return 有效报价列表
     */
    public List<OvernightMarketQuote> fetch(List<String> symbols) {
        List<OvernightMarketQuote> quotes = new ArrayList<>();
        if (CollUtil.isEmpty(symbols)) {
            return quotes;
        }
        String url = "https://qt.gtimg.cn/q=" + String.join(",", symbols);
        try (HttpResponse response = HttpRequest.get(url)
                .timeout(15000)
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://gu.qq.com/")
                .execute()) {
            if (!response.isOk() || StringUtils.isBlank(response.body())) {
                log.warn("盘前晨报美股行情请求失败，状态={}", response.getStatus());
                return quotes;
            }
            String body = new String(response.bodyBytes(), Charset.forName("GBK"));
            String[] lines = body.split("\\n");
            for (String line : lines) {
                OvernightMarketQuote quote = parse(line);
                if (Objects.nonNull(quote)) {
                    quotes.add(quote);
                }
            }
            return quotes;
        } catch (Exception ex) {
            log.warn("盘前晨报美股行情读取失败，原因={}", ex.getMessage());
            return quotes;
        }
    }

    private OvernightMarketQuote parse(String line) {
        if (StringUtils.isBlank(line)) {
            return null;
        }
        int symbolStart = line.indexOf("v_");
        int symbolEnd = line.indexOf('=');
        int contentStart = line.indexOf('"');
        int contentEnd = line.lastIndexOf('"');
        if (symbolStart < 0 || symbolEnd <= symbolStart || contentEnd <= contentStart) {
            return null;
        }
        String[] fields = line.substring(contentStart + 1, contentEnd).split("~", -1);
        if (fields.length <= 32 || StringUtils.isBlank(fields[1])) {
            return null;
        }
        BigDecimal latestPrice = toDecimal(fields[3]);
        BigDecimal pctChg = toDecimal(fields[32]);
        LocalDateTime quoteTime = toTime(fields[30]);
        if (Objects.isNull(latestPrice) || Objects.isNull(pctChg) || Objects.isNull(quoteTime)) {
            return null;
        }
        return OvernightMarketQuote.builder()
                .symbol(line.substring(symbolStart + 2, symbolEnd).trim())
                .name(fields[1].trim())
                .latestPrice(latestPrice)
                .pctChg(pctChg)
                .quoteTime(quoteTime)
                .build();
    }

    private BigDecimal toDecimal(String text) {
        if (StringUtils.isBlank(text) || "--".equals(text)) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDateTime toTime(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        for (DateTimeFormatter quoteTimeFormatter : QUOTE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(text.trim(), quoteTimeFormatter);
            } catch (DateTimeParseException ignored) {
                // 腾讯美股和港股行情使用不同的日期分隔符，继续尝试下一种格式。
            }
        }
        log.warn("腾讯行情时间解析失败，原始时间={}", text);
        return null;
    }
}
