package com.awe.apex.quant.market;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 日线行情客户端（东财前复权优先，新浪仅作非研究场景兜底）
 */
@Slf4j
@Component
public class DailyBarClient {

    public static final String SOURCE_EASTMONEY = "eastmoney";
    public static final String SOURCE_SINA = "sina";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter COMPACT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 拉取日线，自动切换数据源
     *
     * @param code      证券代码
     * @param beginDate 开始日期
     * @param endDate   结束日期
     * @return 日线列表
     */
    public List<BarDaily> fetchDailyBars(String code, String beginDate, String endDate) {
        String pureCode = MarketCodeUtils.normalizeCode(code);
        Exception eastMoneyError = null;
        try {
            List<BarDaily> eastMoneyBars = fetchFromEastMoney(pureCode, beginDate, endDate);
            if (!eastMoneyBars.isEmpty()) {
                return eastMoneyBars;
            }
        } catch (Exception ex) {
            eastMoneyError = ex;
            log.warn("东财前复权日线失败，尝试新浪兜底，code={}, err={}", pureCode, ex.getMessage());
            sleepQuiet(600L);
        }
        try {
            List<BarDaily> sinaBars = fetchFromSina(pureCode, beginDate, endDate);
            if (!sinaBars.isEmpty()) {
                return sinaBars;
            }
            throw new BusinessException("新浪无数据");
        } catch (Exception ex) {
            String msg = "eastmoney: " + messageOf(eastMoneyError) + " | sina: " + ex.getMessage();
            throw new BusinessException("拉取日线失败: " + pureCode + ", " + msg, ex);
        }
    }

    private List<BarDaily> fetchFromEastMoney(String pureCode, String beginDate, String endDate) {
        String secId = MarketCodeUtils.toEastMoneySecId(pureCode);
        String begin = toCompact(beginDate);
        String end = toCompact(endDate);
        String url = "https://push2his.eastmoney.com/api/qt/stock/kline/get"
                + "?secid=" + secId
                + "&fields1=f1,f2,f3,f4,f5,f6"
                + "&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61"
                + "&klt=101&fqt=1"
                + "&beg=" + begin
                + "&end=" + end;
        String body = httpGet(url, "https://quote.eastmoney.com/");
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                throw new BusinessException("行情无数据");
            }
            JsonNode klines = data.path("klines");
            List<BarDaily> bars = new ArrayList<>();
            if (!klines.isArray()) {
                return bars;
            }
            for (JsonNode node : klines) {
                String line = node.asText();
                if (StringUtils.isBlank(line)) {
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length < 7) {
                    continue;
                }
                bars.add(buildBar(
                        pureCode,
                        LocalDate.parse(parts[0], DAY),
                        parts[1],
                        parts[3],
                        parts[4],
                        parts[2],
                        parts[5],
                        parts[6],
                        parts.length > 8 ? parts[8] : null,
                        parts.length > 10 ? parts[10] : null,
                        SOURCE_EASTMONEY
                ));
            }
            log.info("东财日线拉取完成，code={}, bars={}", pureCode, bars.size());
            return bars;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ex.getMessage(), ex);
        }
    }

    private List<BarDaily> fetchFromSina(String pureCode, String beginDate, String endDate) {
        String market = MarketCodeUtils.resolveMarket(pureCode);
        String prefix = "sz";
        if ("SH".equals(market)) {
            prefix = "sh";
        } else if ("BJ".equals(market)) {
            prefix = "bj";
        }
        String symbol = prefix + pureCode;
        String url = "https://money.finance.sina.com.cn/quotes_service/api/json_v2.php/CN_MarketData.getKLineData"
                + "?symbol=" + symbol + "&scale=240&ma=no&datalen=640";
        String body = httpGet(url, "https://finance.sina.com.cn/");
        try {
            JsonNode arr = OBJECT_MAPPER.readTree(body);
            if (!arr.isArray()) {
                throw new BusinessException("新浪行情格式异常");
            }
            LocalDate begin = parseDate(beginDate, LocalDate.now().minusDays(120));
            LocalDate end = parseDate(endDate, LocalDate.now());
            List<BarDaily> bars = new ArrayList<>();
            for (JsonNode node : arr) {
                LocalDate tradeDate = LocalDate.parse(node.path("day").asText(), DAY);
                if (tradeDate.isBefore(begin) || tradeDate.isAfter(end)) {
                    continue;
                }
                bars.add(buildBar(
                        pureCode,
                        tradeDate,
                        node.path("open").asText(),
                        node.path("high").asText(),
                        node.path("low").asText(),
                        node.path("close").asText(),
                        node.path("volume").asText(),
                        null,
                        null,
                        null,
                        SOURCE_SINA
                ));
            }
            log.info("新浪日线拉取完成，code={}, bars={}", pureCode, bars.size());
            if (bars.isEmpty()) {
                throw new BusinessException("新浪无区间数据");
            }
            return bars;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ex.getMessage(), ex);
        }
    }

    private BarDaily buildBar(String code, LocalDate tradeDate, String open, String high, String low,
                              String close, String volume, String amount, String pctChg,
                              String turnoverRate, String source) {
        LocalDateTime now = LocalDateTime.now();
        return BarDaily.builder()
                .code(code)
                .tradeDate(tradeDate)
                .openPrice(toDecimal(open))
                .highPrice(toDecimal(high))
                .lowPrice(toDecimal(low))
                .closePrice(toDecimal(close))
                .volume(toDecimal(volume))
                .amount(toDecimal(amount))
                .pctChg(toDecimal(pctChg))
                .turnoverRate(toDecimal(turnoverRate))
                .source(source)
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
    }

    private String httpGet(String url, String referer) {
        try (HttpResponse response = HttpRequest.get(url)
                .timeout(20000)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept", "application/json,text/plain,*/*")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Connection", "close")
                .header("Referer", referer)
                .execute()) {
            if (!response.isOk()) {
                throw new BusinessException("HTTP " + response.getStatus());
            }
            String body = response.body();
            if (StringUtils.isBlank(body)) {
                throw new BusinessException("空响应");
            }
            return body;
        }
    }

    private BigDecimal toDecimal(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return new BigDecimal(value.trim());
    }

    private String toCompact(String date) {
        if (StringUtils.isBlank(date)) {
            return LocalDate.now().format(COMPACT);
        }
        String value = date.trim();
        if (value.contains("-")) {
            return LocalDate.parse(value, DAY).format(COMPACT);
        }
        return value;
    }

    private LocalDate parseDate(String date, LocalDate defaultDate) {
        if (StringUtils.isBlank(date)) {
            return defaultDate;
        }
        String value = date.trim();
        if (value.contains("-")) {
            return LocalDate.parse(value, DAY);
        }
        return LocalDate.parse(value, COMPACT);
    }

    private void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private String messageOf(Exception ex) {
        return ex == null ? "unknown" : ex.getMessage();
    }
}
