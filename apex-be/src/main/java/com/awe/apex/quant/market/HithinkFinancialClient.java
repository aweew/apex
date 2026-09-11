package com.awe.apex.quant.market;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.JsonUtils;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.config.HithinkFinancialProperties;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 同花顺金融数据服务客户端。
 */
@Slf4j
@Component
public class HithinkFinancialClient {

    private static final ObjectMapper FALLBACK_OBJECT_MAPPER = new ObjectMapper();
    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String SNAPSHOT_PATH = "/api/a-share/prices/snapshot";
    private static final String VALUATION_PATH = "/api/a-share/valuations/snapshot";
    private static final String SOURCE = "hithink";

    @Resource
    private HithinkFinancialProperties properties;

    /**
     * 判断同花顺行情服务是否具备调用条件。
     *
     * @return true=已启用且已配置 API Key
     */
    public boolean isAvailable() {
        return Objects.nonNull(properties)
                && properties.isEnabled()
                && StringUtils.isNotBlank(properties.getApiKey());
    }

    /**
     * 批量查询 A 股实时行情。
     *
     * @param codes Apex 纯数字证券代码
     * @return 按纯数字证券代码索引的行情
     */
    public Map<String, StockBasic> fetchSnapshots(List<String> codes) {
        Map<String, StockBasic> result = new LinkedHashMap<>();
        if (!isAvailable() || CollUtil.isEmpty(codes)) {
            return result;
        }

        List<String> thscodes = new ArrayList<>();
        Map<String, Boolean> seenCodes = new LinkedHashMap<>();
        for (String code : codes) {
            String pureCode = MarketCodeUtils.normalizeCode(code);
            String market = MarketCodeUtils.resolveMarket(code);
            if (StringUtils.isBlank(pureCode)
                    || (!"SH".equals(market) && !"SZ".equals(market) && !"BJ".equals(market))) {
                continue;
            }
            String thscode = pureCode + "." + market;
            if (!seenCodes.containsKey(thscode)) {
                seenCodes.put(thscode, Boolean.TRUE);
                thscodes.add(thscode);
            }
        }
        if (thscodes.isEmpty()) {
            return result;
        }

        int batchSize = Math.max(1, Math.min(properties.getBatchSize(), 100));
        for (int start = 0; start < thscodes.size(); start += batchSize) {
            int end = Math.min(start + batchSize, thscodes.size());
            result.putAll(fetchSnapshotBatch(thscodes.subList(start, end)));
        }
        return result;
    }

    /**
     * 查询单只 A 股实时行情。
     *
     * @param code Apex 证券代码
     * @return 行情快照，未返回有效价格时返回 null
     */
    public StockBasic fetchSnapshot(String code) {
        Map<String, StockBasic> snapshots = fetchSnapshots(List.of(code));
        String pureCode = MarketCodeUtils.normalizeCode(code);
        return snapshots.get(pureCode);
    }

    /**
     * 批量查询 A 股估值快照。
     *
     * @param codes Apex 纯数字证券代码
     * @return 按纯数字证券代码索引的估值字段
     */
    public Map<String, StockBasic> fetchValuations(List<String> codes) {
        Map<String, StockBasic> result = new LinkedHashMap<>();
        if (!isAvailable() || CollUtil.isEmpty(codes)) {
            return result;
        }
        List<String> thscodes = toThscodes(codes);
        int batchSize = Math.max(1, Math.min(properties.getBatchSize(), 100));
        for (int start = 0; start < thscodes.size(); start += batchSize) {
            int end = Math.min(start + batchSize, thscodes.size());
            result.putAll(fetchValuationBatch(thscodes.subList(start, end)));
        }
        return result;
    }

    /**
     * 将同花顺快照转换为 Apex 股票基础行情。
     *
     * @param item 同花顺快照
     * @return Apex 股票基础行情
     */
    StockBasic toStockBasic(HithinkSnapshotItem item) {
        String pureCode = MarketCodeUtils.normalizeCode(item.getThscode());
        String market = MarketCodeUtils.resolveMarket(item.getThscode());
        LocalDateTime quoteTime = Objects.nonNull(item.getTimestamp()) && item.getTimestamp() > 0
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(item.getTimestamp()), SHANGHAI_ZONE)
                : LocalDateTime.now(SHANGHAI_ZONE);
        return StockBasic.builder()
                .code(pureCode)
                .market(market)
                .latestPrice(item.getLastPrice())
                .pctChg(item.getPriceChangeRatioPct())
                .source(SOURCE)
                .quoteTime(quoteTime)
                .createTime(quoteTime)
                .updateTime(quoteTime)
                .deleted(0)
                .build();
    }

    private Map<String, StockBasic> fetchSnapshotBatch(List<String> thscodes) {
        String encodedCodes = URLEncoder.encode(String.join(",", thscodes), StandardCharsets.UTF_8);
        String url = normalizeBaseUrl() + SNAPSHOT_PATH + "?thscodes=" + encodedCodes;
        try (HttpResponse response = HttpRequest.get(url)
                .timeout(Math.max(1000, Math.min(properties.getTimeoutMs(), 30000)))
                .header("X-api-key", properties.getApiKey().trim())
                .header("Accept", "application/json")
                .execute()) {
            if (!response.isOk() || StringUtils.isBlank(response.body())) {
                throw new BusinessException("同花顺行情接口无响应，HTTP " + response.getStatus());
            }
            Map<String, StockBasic> result = parseSnapshotResponse(response.body());
            log.info("同花顺行情批量拉取完成，证券数量={}，有效数量={}", thscodes.size(), result.size());
            return result;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("同花顺行情批量拉取失败，证券数量={}，异常={}", thscodes.size(), ex.getMessage());
            throw new BusinessException("同花顺行情批量拉取失败: " + ex.getMessage(), ex);
        }
    }

    Map<String, StockBasic> parseSnapshotResponse(String responseBody) {
        try {
            JsonNode root = readTree(responseBody);
            checkResponse(root, "行情");
            JsonNode data = root.path("data");
            JsonNode items = data.path("item");
            Map<String, StockBasic> result = new LinkedHashMap<>();
            if (!items.isArray()) {
                return result;
            }
            Long timestamp = data.path("timestamp").isNumber() ? data.path("timestamp").asLong() : null;
            for (JsonNode item : items) {
                HithinkSnapshotItem snapshot = HithinkSnapshotItem.builder()
                        .thscode(text(item, "thscode"))
                        .ticker(text(item, "ticker"))
                        .lastPrice(decimal(item, "last_price"))
                        .priceChangeRatioPct(decimal(item, "price_change_ratio_pct"))
                        .timestamp(timestamp)
                        .build();
                if (StringUtils.isBlank(snapshot.getThscode())
                        || Objects.isNull(snapshot.getLastPrice())
                        || snapshot.getLastPrice().signum() <= 0) {
                    continue;
                }
                StockBasic basic = toStockBasic(snapshot);
                result.put(basic.getCode(), basic);
            }
            return result;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("同花顺行情响应解析失败: " + ex.getMessage(), ex);
        }
    }

    private Map<String, StockBasic> fetchValuationBatch(List<String> thscodes) {
        String encodedCodes = URLEncoder.encode(String.join(",", thscodes), StandardCharsets.UTF_8);
        String url = normalizeBaseUrl() + VALUATION_PATH + "?thscodes=" + encodedCodes;
        try (HttpResponse response = HttpRequest.get(url)
                .timeout(Math.max(1000, Math.min(properties.getTimeoutMs(), 30000)))
                .header("X-api-key", properties.getApiKey().trim())
                .header("Accept", "application/json")
                .execute()) {
            if (!response.isOk() || StringUtils.isBlank(response.body())) {
                throw new BusinessException("同花顺估值接口无响应，HTTP " + response.getStatus());
            }
            Map<String, StockBasic> result = parseValuationResponse(response.body());
            log.info("同花顺估值批量拉取完成，证券数量={}，有效数量={}", thscodes.size(), result.size());
            return result;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("同花顺估值批量拉取失败: " + ex.getMessage(), ex);
        }
    }

    Map<String, StockBasic> parseValuationResponse(String responseBody) {
        try {
            JsonNode root = readTree(responseBody);
            checkResponse(root, "估值");
            JsonNode items = root.path("data").path("item");
            Map<String, StockBasic> result = new LinkedHashMap<>();
            if (!items.isArray()) {
                return result;
            }
            for (JsonNode item : items) {
                String thscode = text(item, "thscode");
                String pureCode = MarketCodeUtils.normalizeCode(thscode);
                if (StringUtils.isBlank(pureCode)) {
                    continue;
                }
                result.put(pureCode, StockBasic.builder()
                        .code(pureCode)
                        .market(MarketCodeUtils.resolveMarket(thscode))
                        .peTtm(decimal(item, "pe_ttm"))
                        .pb(decimal(item, "pb_mrq"))
                        .source(SOURCE)
                        .build());
            }
            return result;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("同花顺估值响应解析失败: " + ex.getMessage(), ex);
        }
    }

    private void checkResponse(JsonNode root, String subject) {
        int code = root.path("code").asInt(-1);
        if (code != 0) {
            String requestId = root.path("request_id").asText("");
            throw new BusinessException("同花顺" + subject + "接口失败，code=" + code
                    + "，request_id=" + requestId);
        }
    }

    private JsonNode readTree(String responseBody) throws Exception {
        try {
            return JsonUtils.getObjectMapper().readTree(responseBody);
        } catch (Exception ignored) {
            return FALLBACK_OBJECT_MAPPER.readTree(responseBody);
        }
    }

    private List<String> toThscodes(List<String> codes) {
        List<String> thscodes = new ArrayList<>();
        Map<String, Boolean> seenCodes = new LinkedHashMap<>();
        for (String code : codes) {
            String pureCode = MarketCodeUtils.normalizeCode(code);
            String market = MarketCodeUtils.resolveMarket(code);
            if (StringUtils.isBlank(pureCode)
                    || (!"SH".equals(market) && !"SZ".equals(market) && !"BJ".equals(market))) {
                continue;
            }
            String thscode = pureCode + "." + market;
            if (!seenCodes.containsKey(thscode)) {
                seenCodes.put(thscode, Boolean.TRUE);
                thscodes.add(thscode);
            }
        }
        return thscodes;
    }

    private String normalizeBaseUrl() {
        String baseUrl = properties.getBaseUrl();
        if (StringUtils.isBlank(baseUrl)) {
            return "https://fuyao.aicubes.cn";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String text(JsonNode node, String fieldName) {
        String text = node.path(fieldName).asText(null);
        return StringUtils.isNotBlank(text) ? text.trim().toUpperCase(Locale.ROOT) : null;
    }

    private BigDecimal decimal(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        if (StringUtils.isBlank(text)) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
