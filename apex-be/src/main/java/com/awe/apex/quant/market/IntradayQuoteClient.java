package com.awe.apex.quant.market;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.IntradayPoint;
import com.awe.apex.quant.domain.dto.StockIntradayResp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 东财分时行情客户端
 */
@Slf4j
@Component
public class IntradayQuoteClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 拉取分时（最近一个交易日，约 241 分钟点）
     *
     * @param code 证券代码
     * @return 分时
     */
    public StockIntradayResp fetch(String code) {
        String pure = MarketCodeUtils.normalizeHoldingCode(code);
        if (StringUtils.isBlank(pure)) {
            throw new BusinessException("代码无效");
        }
        String secId = MarketCodeUtils.toEastMoneySecId(pure);
        String[] hosts = {
                "https://push2delay.eastmoney.com/api/qt/stock/trends2/get",
                "https://push2.eastmoney.com/api/qt/stock/trends2/get",
        };
        Exception last = null;
        for (String host : hosts) {
            String url = host
                    + "?fields1=f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f11,f12,f13"
                    + "&fields2=f51,f52,f53,f54,f55,f56,f57,f58"
                    + "&ut=fa5fd1943c7b386f172d6893dbfba10b"
                    + "&ndays=1&iscr=0&iscca=0"
                    + "&secid=" + secId
                    + "&_=" + System.currentTimeMillis();
            try (HttpResponse response = HttpRequest.get(url)
                    .timeout(12000)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://quote.eastmoney.com/")
                    .header("Accept", "*/*")
                    .execute()) {
                if (!response.isOk() || StringUtils.isBlank(response.body())) {
                    throw new BusinessException("分时接口无响应");
                }
                return parse(pure, response.body());
            } catch (Exception ex) {
                last = ex;
                log.debug("分时拉取失败 host={}, code={}, err={}", host, pure, ex.getMessage());
            }
        }
        throw new BusinessException("拉取分时失败: " + pure + ", "
                + (Objects.nonNull(last) ? last.getMessage() : "unknown"), last);
    }

    private StockIntradayResp parse(String code, String body) throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(body);
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            throw new BusinessException("分时无数据");
        }
        BigDecimal preClose = toDecimal(data.path("prePrice").asText(null));
        if (Objects.isNull(preClose)) {
            preClose = toDecimal(data.path("preClose").asText(null));
        }
        String name = blankToNull(data.path("name").asText(null));
        JsonNode trends = data.path("trends");
        List<IntradayPoint> points = new ArrayList<>();
        String tradeDate = null;
        if (trends.isArray()) {
            for (JsonNode node : trends) {
                String line = node.asText();
                if (StringUtils.isBlank(line)) {
                    continue;
                }
                // 时间,开,收,高,低,量,额,均价
                String[] parts = line.split(",");
                if (parts.length < 8) {
                    continue;
                }
                String datetime = parts[0].trim();
                String time = datetime;
                if (datetime.length() >= 16) {
                    time = datetime.substring(11, 16);
                    if (Objects.isNull(tradeDate)) {
                        tradeDate = datetime.substring(0, 10);
                    }
                }
                points.add(IntradayPoint.builder()
                        .datetime(datetime)
                        .time(time)
                        .price(toDecimal(parts[2]))
                        .avgPrice(toDecimal(parts[7]))
                        .volume(toDecimal(parts[5]))
                        .amount(toDecimal(parts[6]))
                        .build());
            }
        }
        if (points.isEmpty()) {
            throw new BusinessException("分时点为空");
        }
        return StockIntradayResp.builder()
                .code(code)
                .name(name)
                .preClose(preClose)
                .tradeDate(tradeDate)
                .points(points)
                .note("东财分时 · " + (Objects.nonNull(tradeDate) ? tradeDate : "") + " · " + points.size() + " 点")
                .build();
    }

    private BigDecimal toDecimal(String text) {
        if (StringUtils.isBlank(text) || "-".equals(text) || "--".equals(text)) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private String blankToNull(String text) {
        return StringUtils.isBlank(text) ? null : text.trim();
    }
}
