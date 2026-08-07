package com.awe.apex.quant.decision;

import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DecisionSnapshotJsonTest {

    @Test
    void roundTripsBriefingDate() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        MarketBriefingResp source = MarketBriefingResp.builder()
                .asOf(LocalDate.of(2026, 8, 7))
                .stance("均衡")
                .build();

        MarketBriefingResp restored = mapper.readValue(
                mapper.writeValueAsString(source), MarketBriefingResp.class);

        assertEquals(source.getAsOf(), restored.getAsOf());
    }
}
