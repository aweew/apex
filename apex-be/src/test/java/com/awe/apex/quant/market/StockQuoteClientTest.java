package com.awe.apex.quant.market;

import com.awe.apex.quant.domain.entity.StockBasic;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StockQuoteClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void mapsEastMoneyPeVariantsToTheirCorrectFields() throws Exception {
        JsonNode data = OBJECT_MAPPER.readTree("{\"f162\":1502,\"f163\":1988,\"f164\":1979}");
        StockBasic basic = new StockBasic();

        StockQuoteClient.applyEastMoneyValuationFields(basic, data);

        assertEquals(new BigDecimal("15.0200"), basic.getPeDynamic());
        assertEquals(new BigDecimal("19.8800"), basic.getPeStatic());
        assertEquals(new BigDecimal("19.7900"), basic.getPeTtm());
    }

    @Test
    void mapsTencentPeToDynamicRatherThanTtm() {
        String[] parts = new String[47];
        parts[39] = "15.02";
        StockBasic basic = new StockBasic();

        StockQuoteClient.applyTencentValuationFields(basic, parts);

        assertEquals(new BigDecimal("15.02"), basic.getPeDynamic());
        assertNull(basic.getPeTtm());
    }
}
