package com.awe.apex.quant.market;

import com.awe.apex.quant.domain.dto.OpeningAuctionIndexResp;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpeningAuctionQuoteClientTest {

    @Test
    void parsesTencentIndexAuctionQuotes() {
        OpeningAuctionQuoteClient client = new OpeningAuctionQuoteClient();
        String response = """
                v_s_sh000300="1~沪深300~000300~4542.25~-20.88~-0.46~107689248~29712433~~544634.62~ZS~";
                v_s_sz399006="51~创业板指~399006~3382.81~-49.08~-1.43~103151425~27919979~~181735.96~ZS~";
                """;

        List<OpeningAuctionIndexResp> indexes = client.parse(response, LocalDateTime.of(2026, 8, 25, 9, 20));

        assertEquals(2, indexes.size());
        assertEquals("沪深300", indexes.get(0).getName());
        assertEquals(new BigDecimal("4542.25"), indexes.get(0).getLatestPrice());
        assertEquals(new BigDecimal("-0.46"), indexes.get(0).getPctChg());
        assertEquals("创业板指", indexes.get(1).getName());
    }
}
