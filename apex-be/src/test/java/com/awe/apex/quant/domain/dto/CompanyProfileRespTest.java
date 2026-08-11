package com.awe.apex.quant.domain.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CompanyProfileRespTest {

    @Test
    void serializeAShareFieldsWithCamelCaseNames() throws Exception {
        CompanyProfileResp response = CompanyProfileResp.builder()
                .aCode("600519")
                .aName("贵州茅台")
                .build();

        JsonNode json = new ObjectMapper().valueToTree(response);

        assertEquals("600519", json.path("aCode").asText());
        assertEquals("贵州茅台", json.path("aName").asText());
        assertFalse(json.has("acode"));
        assertFalse(json.has("aname"));
    }
}
