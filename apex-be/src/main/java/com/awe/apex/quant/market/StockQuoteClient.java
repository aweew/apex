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

/**
 * 股票基本信息/快照行情客户端（新浪优先，东财/腾讯补充估值与行业）
 */
@Slf4j
@Component
public class StockQuoteClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter LIST_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final BigDecimal YI = new BigDecimal("100000000");

    /**
     * 拉取并组装基本信息
     *
     * @param code 证券代码
     * @return 基本信息
     */
    public StockBasic fetchBasic(String code) {
        String pure = MarketCodeUtils.normalizeCode(code);
        String market = MarketCodeUtils.resolveMarket(pure);
        StockBasic basic = fetchFromSina(pure, market);
        enrichFromEastMoney(basic);
        if (needValuationFallback(basic)) {
            enrichFromTencent(basic);
        }
        if (StringUtils.isBlank(basic.getIndustry())) {
            enrichIndustryFromEastMoney(basic);
        }
        return basic;
    }

    private StockBasic fetchFromSina(String code, String market) {
        String symbol = toSinaSymbol(code, market);
        String url = "https://hq.sinajs.cn/list=" + symbol;
        try (HttpResponse response = HttpRequest.get(url)
                .timeout(12000)
                .header("User-Agent", "Mozilla/5.0")
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
            if (parts.length < 10 || StringUtils.isBlank(parts[0])) {
                throw new BusinessException("未找到股票: " + code);
            }
            BigDecimal price = toDecimal(parts[3]);
            BigDecimal prevClose = toDecimal(parts[2]);
            BigDecimal pct = null;
            if (Objects.nonNull(price) && Objects.nonNull(prevClose) && prevClose.signum() > 0) {
                pct = price.subtract(prevClose)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(prevClose, 4, RoundingMode.HALF_UP);
            }
            LocalDateTime now = LocalDateTime.now();
            String name = parts[0];
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
        try (HttpResponse response = HttpRequest.get(url)
                .timeout(10000)
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://quote.eastmoney.com/")
                .execute()) {
            if (!response.isOk() || StringUtils.isBlank(response.body())) {
                return;
            }
            JsonNode data = OBJECT_MAPPER.readTree(response.body()).path("data");
            if (data.isMissingNode() || data.isNull()) {
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
        } catch (Exception ex) {
            log.warn("东财估值补充失败，code={}, err={}", basic.getCode(), ex.getMessage());
        }
    }

    private void enrichFromTencent(StockBasic basic) {
        String symbol = toSinaSymbol(basic.getCode(), basic.getMarket());
        String url = "https://qt.gtimg.cn/q=" + symbol;
        try (HttpResponse response = HttpRequest.get(url)
                .timeout(10000)
                .header("User-Agent", "Mozilla/5.0")
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
            log.warn("腾讯估值补充失败，code={}, err={}", basic.getCode(), ex.getMessage());
        }
    }

    private void enrichIndustryFromEastMoney(StockBasic basic) {
        String market = basic.getMarket();
        if (StringUtils.isBlank(market)) {
            market = MarketCodeUtils.resolveMarket(basic.getCode());
        }
        String codeParam = market + basic.getCode();
        String url = "https://emweb.securities.eastmoney.com/PC_HSF10/CompanySurvey/CompanySurveyAjax?code="
                + codeParam;
        try (HttpResponse response = HttpRequest.get(url)
                .timeout(12000)
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://emweb.securities.eastmoney.com/")
                .execute()) {
            if (!response.isOk() || StringUtils.isBlank(response.body())) {
                return;
            }
            String industry = OBJECT_MAPPER.readTree(response.body())
                    .path("jbzl")
                    .path("sshy")
                    .asText(null);
            industry = blankToNull(industry);
            if (StringUtils.isNotBlank(industry)) {
                basic.setIndustry(industry);
                appendSource(basic, "em-f10");
            }
        } catch (Exception ex) {
            log.warn("东财行业补充失败，code={}, err={}", basic.getCode(), ex.getMessage());
        }
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
        if ("SH".equals(market)) {
            return "sh" + code;
        }
        if ("BJ".equals(market)) {
            return "bj" + code;
        }
        return "sz" + code;
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
