package com.awe.apex.quant.market;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.JsonUtils;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.ScreenerMarketSnapshot;
import com.awe.apex.quant.domain.dto.ScreenerMarketSnapshotBatch;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 东财全A实时选股截面客户端
 */
@Slf4j
@Component
public class ScreenerMarketSnapshotClient {

    private static final String QUERY = "?pn=1&pz=6000&po=1&np=1&fltt=2&invt=2&fid=f3"
            + "&fs=m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23,m:0+t:81+s:2048"
            + "&fields=f2,f3,f6,f8,f9,f10,f12,f13,f14,f20,f21,f23,f100,f124";

    /**
     * 拉取全A实时选股截面。
     *
     * @return 实时截面批次
     */
    public ScreenerMarketSnapshotBatch fetchAll() {
        String[] hosts = {
                "https://push2delay.eastmoney.com/api/qt/clist/get",
                "https://push2.eastmoney.com/api/qt/clist/get",
        };
        Exception last = null;
        for (String host : hosts) {
            try (HttpResponse response = HttpRequest.get(host + QUERY + "&_=" + System.currentTimeMillis())
                    .timeout(15000)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://quote.eastmoney.com/")
                    .header("Accept", "*/*")
                    .execute()) {
                if (!response.isOk() || StringUtils.isBlank(response.body())) {
                    throw new BusinessException("实时截面接口无响应");
                }
                return parse(response.body());
            } catch (Exception ex) {
                last = ex;
                log.warn("实时选股截面拉取失败 host={}, error={}", host, ex.getMessage());
            }
        }
        throw new BusinessException("实时选股截面拉取失败: "
                + (Objects.nonNull(last) ? last.getMessage() : "unknown"), last);
    }

    ScreenerMarketSnapshotBatch parse(String body) throws Exception {
        JsonNode root = JsonUtils.getObjectMapper().readTree(body);
        JsonNode diff = root.path("data").path("diff");
        if (!diff.isArray()) {
            throw new BusinessException("实时截面缺少股票列表");
        }
        List<ScreenerMarketSnapshot> items = new ArrayList<>();
        LocalDateTime asOf = null;
        for (JsonNode row : diff) {
            String code = text(row, "f12");
            if (StringUtils.isBlank(code)) {
                continue;
            }
            LocalDateTime quoteTime = timestamp(row.path("f124"));
            if (Objects.nonNull(quoteTime) && (Objects.isNull(asOf) || quoteTime.isAfter(asOf))) {
                asOf = quoteTime;
            }
            items.add(ScreenerMarketSnapshot.builder()
                    .code(code)
                    .name(text(row, "f14"))
                    .market(resolveMarket(code, row.path("f13").asInt(-1)))
                    .latestPrice(decimal(row.path("f2")))
                    .pctChg(decimal(row.path("f3")))
                    .amount(decimal(row.path("f6")))
                    .turnoverRate(decimal(row.path("f8")))
                    .volumeRatio(decimal(row.path("f10")))
                    .totalMv(decimal(row.path("f20")))
                    .circMv(decimal(row.path("f21")))
                    .pb(decimal(row.path("f23")))
                    .industry(text(row, "f100"))
                    .quoteTime(quoteTime)
                    .build());
        }
        if (CollUtil.isEmpty(items)) {
            throw new BusinessException("实时截面股票列表为空");
        }
        return ScreenerMarketSnapshotBatch.builder()
                .source("eastmoney-clist")
                .asOf(asOf)
                .items(items)
                .build();
    }

    private BigDecimal decimal(JsonNode node) {
        if (Objects.isNull(node) || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        String value = node.asText();
        if (StringUtils.isBlank(value) || "-".equals(value) || "--".equals(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDateTime timestamp(JsonNode node) {
        if (Objects.isNull(node) || !node.canConvertToLong() || node.asLong() <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(node.asLong()), ZoneId.systemDefault());
    }

    private String text(JsonNode row, String field) {
        String value = row.path(field).asText(null);
        return StringUtils.isBlank(value) || "-".equals(value) || "--".equals(value) ? null : value.trim();
    }

    private String resolveMarket(String code, int marketId) {
        if (marketId == 1 || code.startsWith("6")) {
            return "SH";
        }
        if (marketId == 0 && !code.startsWith("8") && !code.startsWith("4") && !code.startsWith("9")) {
            return "SZ";
        }
        return "BJ";
    }
}
