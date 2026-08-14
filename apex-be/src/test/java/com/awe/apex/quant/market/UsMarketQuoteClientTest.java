package com.awe.apex.quant.market;

import com.awe.apex.quant.domain.dto.OvernightMarketQuote;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UsMarketQuoteClientTest {

    @Test
    void parsesTencentUsQuoteResponse() {
        UsMarketQuoteClient client = new UsMarketQuoteClient();
        String response = "v_usNVDA=\"200~英伟达~NVDA.OQ~225.30~224.09~225.06~98867226~0~0~225.42~600~0~0~0~0~0~0~0~0~225.50~100~0~0~0~0~0~0~0~0~~2026-08-13 16:00:01~1.21~0.54~227.23~223.71~USD\";";

        OvernightMarketQuote quote = ReflectionTestUtils.invokeMethod(client, "parse", response);

        assertEquals("usNVDA", quote.getSymbol());
        assertEquals("英伟达", quote.getName());
        assertEquals(new BigDecimal("225.30"), quote.getLatestPrice());
        assertEquals(new BigDecimal("0.54"), quote.getPctChg());
        assertEquals(LocalDateTime.of(2026, 8, 13, 16, 0, 1), quote.getQuoteTime());
    }
}
