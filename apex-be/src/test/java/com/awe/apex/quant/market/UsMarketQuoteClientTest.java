package com.awe.apex.quant.market;

import com.awe.apex.quant.domain.dto.OvernightMarketQuote;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @Test
    void parsesTencentGoldenDragonIndexResponse() {
        UsMarketQuoteClient client = new UsMarketQuoteClient();
        String response = "v_usHXC=\"100~纳斯达克中国金龙指数~HXC~6183.99~6230.03~6186.35~0~0~0~6184.00~0~0~0~0~0~0~0~0~0~6199.33~0~0~0~0~0~0~0~0~0~~2026-08-27 16:00:00~-46.04~-0.74~6199.33~6153.41~USD\";";

        OvernightMarketQuote quote = ReflectionTestUtils.invokeMethod(client, "parse", response);

        assertEquals("usHXC", quote.getSymbol());
        assertEquals("纳斯达克中国金龙指数", quote.getName());
        assertEquals(new BigDecimal("6183.99"), quote.getLatestPrice());
        assertEquals(new BigDecimal("-0.74"), quote.getPctChg());
        assertEquals(LocalDateTime.of(2026, 8, 27, 16, 0), quote.getQuoteTime());
    }

    @Test
    void rejectsTencentQuoteWithoutValidSourceTime() {
        UsMarketQuoteClient client = new UsMarketQuoteClient();
        String response = "v_usHXC=\"100~纳斯达克中国金龙指数~HXC~6183.99~6230.03~6186.35~0~0~0~"
                + "6184.00~0~0~0~0~0~0~0~0~0~6199.33~0~0~0~0~0~0~0~0~0~~invalid-time~"
                + "-46.04~-0.74~6199.33~6153.41~USD\";";

        OvernightMarketQuote quote = ReflectionTestUtils.invokeMethod(client, "parse", response);

        assertNull(quote);
    }
}
