package com.awe.apex.quant.market;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.OpeningAuctionIndexResp;
import com.awe.apex.quant.domain.dto.OpeningAuctionResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A 股集合竞价指数行情客户端。
 */
@Slf4j
@Component
public class OpeningAuctionQuoteClient {

    private static final String QUOTE_URL = "https://qt.gtimg.cn/q=s_sh000300,s_sz399006";
    private static final LocalTime AUCTION_START_TIME = LocalTime.of(9, 15);
    private static final LocalTime AUCTION_CONFIRM_TIME = LocalTime.of(9, 25);
    private static final LocalTime MARKET_OPEN_TIME = LocalTime.of(9, 30);

    /**
     * 读取当前集合竞价确认信息。
     *
     * @return 竞价时段的沪深300和创业板指报价；其他时段返回明确状态
     */
    public OpeningAuctionResp fetch() {
        LocalDateTime now = LocalDateTime.now();
        if (!TradingCalendar.isTradingDay(now.toLocalDate())) {
            return buildUnavailable("CLOSED", "非交易日，无集合竞价", now);
        }
        if (now.toLocalTime().isBefore(AUCTION_START_TIME)) {
            return buildUnavailable("WAITING", "集合竞价将于 09:15 开始", now);
        }
        if (!now.toLocalTime().isBefore(MARKET_OPEN_TIME)) {
            return buildUnavailable("CLOSED", "集合竞价已结束，请结合开盘后的盘面承接判断", now);
        }
        try (HttpResponse response = HttpRequest.get(QUOTE_URL)
                .timeout(3000)
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://gu.qq.com/")
                .execute()) {
            if (!response.isOk() || StringUtils.isBlank(response.body())) {
                log.warn("集合竞价指数行情请求失败，status={}", response.getStatus());
                return buildUnavailable("UNAVAILABLE", "集合竞价报价暂未获取", now);
            }
            List<OpeningAuctionIndexResp> indexes = parse(new String(response.bodyBytes(), Charset.forName("GBK")), now);
            if (CollUtil.isEmpty(indexes)) {
                return buildUnavailable("UNAVAILABLE", "集合竞价报价暂未获取", now);
            }
            boolean confirmed = !now.toLocalTime().isBefore(AUCTION_CONFIRM_TIME);
            return OpeningAuctionResp.builder()
                    .state(confirmed ? "CONFIRMED" : "AUCTION")
                    .stateDesc(confirmed ? "09:25 开盘确认" : "竞价进行中，价格仍会变动")
                    .available(true)
                    .asOf(now)
                    .indexes(indexes)
                    .build();
        } catch (Exception ex) {
            log.warn("集合竞价指数行情读取失败，reason={}", ex.getMessage());
            return buildUnavailable("UNAVAILABLE", "集合竞价报价暂未获取", now);
        }
    }

    List<OpeningAuctionIndexResp> parse(String response, LocalDateTime quoteTime) {
        List<OpeningAuctionIndexResp> indexes = new ArrayList<>();
        if (StringUtils.isBlank(response)) {
            return indexes;
        }
        String[] lines = response.split("\\n");
        for (String line : lines) {
            int symbolStart = line.indexOf("v_s_");
            int symbolEnd = line.indexOf('=');
            int contentStart = line.indexOf('"');
            int contentEnd = line.lastIndexOf('"');
            if (symbolStart < 0 || symbolEnd <= symbolStart || contentEnd <= contentStart) {
                continue;
            }
            String[] fields = line.substring(contentStart + 1, contentEnd).split("~", -1);
            if (fields.length < 6 || StringUtils.isBlank(fields[1])) {
                continue;
            }
            BigDecimal latestPrice = toDecimal(fields[3]);
            BigDecimal pctChg = toDecimal(fields[5]);
            if (Objects.isNull(latestPrice) || Objects.isNull(pctChg)) {
                continue;
            }
            indexes.add(OpeningAuctionIndexResp.builder()
                    .code(line.substring(symbolStart + 4, symbolEnd).trim())
                    .name(fields[1].trim())
                    .latestPrice(latestPrice)
                    .pctChg(pctChg)
                    .quoteTime(quoteTime)
                    .build());
        }
        return indexes;
    }

    private OpeningAuctionResp buildUnavailable(String state, String stateDesc, LocalDateTime now) {
        return OpeningAuctionResp.builder()
                .state(state)
                .stateDesc(stateDesc)
                .available(false)
                .asOf(now)
                .indexes(List.of())
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
