package com.awe.apex.quant.domain.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ExternalMarketItemRespTest {

    @Test
    void serializeAShareImpactWithCamelCaseName() {
        ExternalMarketItemResp response = ExternalMarketItemResp.builder()
                .aShareImpact("黄金上涨时关注避险情绪")
                .build();

        JsonNode responseJson = new ObjectMapper().valueToTree(response);

        assertEquals("黄金上涨时关注避险情绪", responseJson.path("aShareImpact").asText());
        assertFalse(responseJson.has("ashareImpact"));
    }

    @Test
    void deserializeLegacyAShareImpactCacheField() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        ExternalMarketItemResp response = objectMapper.readValue(
                "{\"ashareImpact\":\"旧缓存中的影响说明\"}", ExternalMarketItemResp.class);

        assertEquals("旧缓存中的影响说明", response.getAShareImpact());
    }
}
