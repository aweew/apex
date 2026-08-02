package com.awe.apex.quant.market;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 股票基本信息/快照行情客户端（新浪行情 + 腾讯估值优先；东财可选且带熔断）
 */
@Slf4j
@Component
public class StockQuoteClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter LIST_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final BigDecimal YI = new BigDecimal("100000000");

    /** 东财连续失败次数达到阈值后熔断 */
    private static final int EM_FAIL_THRESHOLD = 3;
    /** 熔断时长（毫秒） */
    private static final long EM_COOLDOWN_MS = 10 * 60 * 1000L;

    private final AtomicInteger eastMoneyFailCount = new AtomicInteger(0);
    private final AtomicLong eastMoneyCooldownUntil = new AtomicLong(0);

    /**
     * 拉取并组装基本信息
     *
     * @param code 证券代码
     * @return 基本信息
     */
    public StockBasic fetchBasic(String code) {
        String pure = MarketCodeUtils.normalizeHoldingCode(code);
        String market = MarketCodeUtils.resolveMarket(pure);
        StockBasic basic = fetchFromSina(pure, market);
        // 港股估值/行业补充易踩空，有现价即可；A 股继续补估值
        if (!"HK".equals(market) && needValuationFallback(basic)) {
            enrichFromTencent(basic);
        }
        if (!"HK".equals(market) && needValuationFallback(basic) && !isEastMoneyCoolingDown()) {
            enrichFromEastMoney(basic);
        }
        if (!"HK".equals(market) && StringUtils.isBlank(basic.getIndustry()) && !isEastMoneyCoolingDown()) {
            enrichIndustryFromEastMoney(basic);
        }
        return basic;
    }

    private StockBasic fetchFromSina(String code, String market) {
        String symbol = toSinaSymbol(code, market);
        String url = "https://hq.sinajs.cn/list=" + symbol;
        try (HttpResponse response = HttpRequest.get(url)
                .timeout(12000)
                .header("User-Agent", browserUa())
                .header("Referer", "https://finance.sina.com.cn")
                .execute()) {
            if (!response.isOk()) {
                throw new BusinessException("新浪行情 HTTP " + response.getStatus());
            }
            String body = response.body();
            if (response.bodyBytes() != null) {
                body = new String(response.bodyBytes(), Charset.forName("GBK"));
            }
            int start = body.indexOf('"');
            int end = body.lastIndexOf('"');
            if (start < 0 || end <= start) {
                throw new BusinessException("新浪行情格式异常");
            }
            String[] parts = body.substring(start + 1, end).split(",");
            if (parts.length < 4 || StringUtils.isBlank(parts[0])) {
                throw new BusinessException("未找到股票: " + code);
            }
            String name;
            BigDecimal price;
            BigDecimal pct;
            if ("HK".equals(market)) {
                // 港股：英名,中文名,开盘,昨收,最高,最低,现价,涨跌额,涨跌幅,...
                if (parts.length < 9) {
                    throw new BusinessException("未找到港股: " + code);
                }
                name = StringUtils.isNotBlank(parts[1]) ? parts[1] : parts[0];
                price = toDecimal(parts[6]);
                pct = toDecimal(parts[8]);
            } else {
                if (parts.length < 10) {
                    throw new BusinessException("未找到股票: " + code);
                }
                name = parts[0];
                price = toDecimal(parts[3]);
                BigDecimal prevClose = toDecimal(parts[2]);
                pct = null;
                if (Objects.nonNull(price) && Objects.nonNull(prevClose) && prevClose.signum() > 0) {
                    pct = price.subtract(prevClose)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(prevClose, 4, RoundingMode.HALF_UP);
                }
            }
            LocalDateTime now = LocalDateTime.now();
            return StockBasic.builder()
                    .code(code)
                    .name(name)
                    .market(market)
                    .stFlag(name.toUpperCase().contains("ST") ? 1 : 0)
                    .latestPrice(price)
                    .pctChg(pct)
                    .source("sina")
                    .quoteTime(now)
                    .createTime(now)
                    .updateTime(now)
                    .deleted(0)
                    .build();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("拉取基本信息失败: " + code + ", " + ex.getMessage(), ex);
        }
    }

    private void enrichFromEastMoney(StockBasic basic) {
        String secId = MarketCodeUtils.toEastMoneySecId(basic.getCode());
        String url = "https://push2.eastmoney.com/api/qt/stock/get?secid=" + secId
                + "&fields=f57,f58,f116,f117,f162,f167,f173,f189,f127";
        try (HttpResponse response = httpGet(url, "https://quote.eastmoney.com/")) {
            if (!response.isOk() || StringUtils.isBlank(response.body())) {
                markEastMoneyFail("empty/http", basic.getCode());
                return;
            }
            JsonNode data = OBJECT_MAPPER.readTree(response.body()).path("data");
            if (data.isMissingNode() || data.isNull()) {
                markEastMoneyFail("no-data", basic.getCode());
                return;
            }
            if (StringUtils.isBlank(basic.getName())) {
                basic.setName(data.path("f58").asText(null));
            }
            // 东财估值字段常见为 *100
            if (Objects.isNull(basic.getPeTtm())) {
                basic.setPeTtm(scaleDiv100(data.path("f162").asText(null)));
            }
            if (Objects.isNull(basic.getPb())) {
                basic.setPb(scaleDiv100(data.path("f167").asText(null)));
            }
            if (Objects.isNull(basic.getTotalMv())) {
                basic.setTotalMv(toDecimal(data.path("f116").asText(null)));
            }
            if (Objects.isNull(basic.getCircMv())) {
                basic.setCircMv(toDecimal(data.path("f117").asText(null)));
            }
            if (StringUtils.isBlank(basic.getIndustry())) {
                basic.setIndustry(blankToNull(data.path("f127").asText(null)));
            }
            String list = data.path("f189").asText(null);
            if (StringUtils.isNotBlank(list) && list.length() == 8 && Objects.isNull(basic.getListDate())) {
                basic.setListDate(LocalDate.parse(list, LIST_DAY));
            }
            appendSource(basic, "eastmoney");
            markEastMoneySuccess();
        } catch (Exception ex) {
            markEastMoneyFail(ex.getMessage(), basic.getCode());
        }
    }

    private void enrichFromTencent(StockBasic basic) {
        String symbol = toSinaSymbol(basic.getCode(), basic.getMarket());
        String url = "https://qt.gtimg.cn/q=" + symbol;
        try (HttpResponse response = HttpRequest.get(url)
                .timeout(10000)
                .header("User-Agent", browserUa())
                .header("Referer", "https://gu.qq.com/")
                .execute()) {
            if (!response.isOk() || response.bodyBytes() == null) {
                return;
            }
            String body = new String(response.bodyBytes(), Charset.forName("GBK"));
            int start = body.indexOf('"');
            int end = body.lastIndexOf('"');
            if (start < 0 || end <= start) {
                return;
            }
            String[] parts = body.substring(start + 1, end).split("~");
            if (parts.length < 47) {
                return;
            }
            if (StringUtils.isBlank(basic.getName()) && StringUtils.isNotBlank(parts[1])) {
                basic.setName(parts[1]);
            }
            if (Objects.isNull(basic.getLatestPrice())) {
                basic.setLatestPrice(toDecimal(parts[3]));
            }
            if (Objects.isNull(basic.getPctChg())) {
                basic.setPctChg(toDecimal(parts[32]));
            }
            if (Objects.isNull(basic.getPeTtm())) {
                basic.setPeTtm(toDecimal(parts[39]));
            }
            if (Objects.isNull(basic.getPb())) {
                basic.setPb(toDecimal(parts[46]));
            }
            if (Objects.isNull(basic.getCircMv())) {
                basic.setCircMv(yiToYuan(parts[44]));
            }
            if (Objects.isNull(basic.getTotalMv())) {
                basic.setTotalMv(yiToYuan(parts[45]));
            }
            appendSource(basic, "tencent");
        } catch (Exception ex) {
            log.debug("腾讯估值补充失败，code={}, err={}", basic.getCode(), ex.getMessage());
        }
    }

    private void enrichIndustryFromEastMoney(StockBasic basic) {
        String pureCode = basic.getCode();
        String url = "https://datacenter.eastmoney.com/securities/api/data/v1/get"
                + "?reportName=RPT_F10_ORG_BASICINFO"
                + "&columns=BOARD_NAME_2LEVEL,BOARD_NAME_1LEVEL,EM2016"
                + "&quoteColumns="
                + "&filter=(SECURITY_CODE%3D%22" + pureCode + "%22)"
                + "&pageNumber=1&pageSize=1&sortTypes=&sortColumns=&source=HSF10&client=PC";
        try (HttpResponse response = httpGet(url, "https://emweb.securities.eastmoney.com/")) {
            if (!response.isOk() || StringUtils.isBlank(response.body())) {
                markEastMoneyFail("industry-empty", basic.getCode());
                return;
            }
            JsonNode rows = OBJECT_MAPPER.readTree(response.body()).path("result").path("data");
            if (!rows.isArray() || rows.isEmpty()) {
                markEastMoneyFail("industry-empty", basic.getCode());
                return;
            }
            JsonNode row = rows.get(0);
            String industry = blankToNull(row.path("BOARD_NAME_2LEVEL").asText(null));
            if (StringUtils.isBlank(industry)) {
                industry = blankToNull(row.path("EM2016").asText(null));
            }
            if (StringUtils.isBlank(industry)) {
                industry = blankToNull(row.path("BOARD_NAME_1LEVEL").asText(null));
            }
            if (StringUtils.isNotBlank(industry)) {
                basic.setIndustry(industry);
                appendSource(basic, "em-f10-l2");
                markEastMoneySuccess();
            }
        } catch (Exception ex) {
            markEastMoneyFail(ex.getMessage(), basic.getCode());
        }
    }

    private HttpResponse httpGet(String url, String referer) {
        return HttpRequest.get(url)
                .timeout(10000)
                .header("User-Agent", browserUa())
                .header("Accept", "application/json,text/plain,*/*")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Connection", "close")
                .header("Referer", referer)
                .execute();
    }

    private boolean isEastMoneyCoolingDown() {
        return System.currentTimeMillis() < eastMoneyCooldownUntil.get();
    }

    private void markEastMoneySuccess() {
        eastMoneyFailCount.set(0);
    }

    private void markEastMoneyFail(String err, String code) {
        int fails = eastMoneyFailCount.incrementAndGet();
        if (fails >= EM_FAIL_THRESHOLD) {
            long until = System.currentTimeMillis() + EM_COOLDOWN_MS;
            eastMoneyCooldownUntil.set(until);
            eastMoneyFailCount.set(0);
            log.warn("东财接口连续失败，熔断 {} 分钟（不再逐票请求）。lastCode={}, err={}",
                    EM_COOLDOWN_MS / 60000, code, err);
            return;
        }
        log.debug("东财补充失败（{}/{}），code={}, err={}", fails, EM_FAIL_THRESHOLD, code, err);
    }

    private boolean needValuationFallback(StockBasic basic) {
        return Objects.isNull(basic.getPeTtm())
                || Objects.isNull(basic.getPb())
                || Objects.isNull(basic.getTotalMv())
                || Objects.isNull(basic.getCircMv());
    }

    private void appendSource(StockBasic basic, String tag) {
        String source = basic.getSource();
        if (StringUtils.isBlank(source)) {
            basic.setSource(tag);
            return;
        }
        if (!source.contains(tag)) {
            basic.setSource(source + "+" + tag);
        }
    }

    private String toSinaSymbol(String code, String market) {
        if ("HK".equals(market)) {
            return "hk" + code;
        }
        if ("SH".equals(market)) {
            return "sh" + code;
        }
        if ("BJ".equals(market)) {
            return "bj" + code;
        }
        return "sz" + code;
    }

    private String browserUa() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
    }

    private BigDecimal yiToYuan(String yiValue) {
        BigDecimal yi = toDecimal(yiValue);
        if (Objects.isNull(yi)) {
            return null;
        }
        return yi.multiply(YI).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleDiv100(String value) {
        BigDecimal num = toDecimal(value);
        if (Objects.isNull(num)) {
            return null;
        }
        return num.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal toDecimal(String value) {
        if (StringUtils.isBlank(value) || "-".equals(value) || "null".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private String blankToNull(String value) {
        return StringUtils.isBlank(value) || "-".equals(value) ? null : value.trim();
    }
}
