package com.awe.apex.quant.market;

import com.awe.apex.quant.domain.dto.OvernightMarketQuote;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GlobalFuturesQuoteClientTest {

    @Test
    void parsesSinaFtseA50ContinuousFutureResponse() {
        GlobalFuturesQuoteClient client = new GlobalFuturesQuoteClient();
        String response = "var hq_str_hf_CHA50CFD=\"14746.940,,14741.000,14742.000,14795.000,"
                + "14648.000,12:43:03,14728.000,14729.000,781522,15,17,2026-08-28,"
                + "富时中国A50期货,198848\";";

        OvernightMarketQuote quote = ReflectionTestUtils.invokeMethod(client, "parse", "hf_CHA50CFD", response);

        assertEquals("hf_CHA50CFD", quote.getSymbol());
        assertEquals("富时中国A50期货", quote.getName());
        assertEquals(new BigDecimal("14746.940"), quote.getLatestPrice());
        assertEquals(new BigDecimal("0.13"), quote.getPctChg());
        assertEquals(LocalDateTime.of(2026, 8, 28, 12, 43, 3), quote.getQuoteTime());
    }

    @Test
    void rejectsSinaFutureWithoutValidSourceTime() {
        GlobalFuturesQuoteClient client = new GlobalFuturesQuoteClient();
        String response = "var hq_str_hf_CHA50CFD=\"14746.940,,14741.000,14742.000,14795.000,"
                + "14648.000,invalid-time,14728.000,14729.000,781522,15,17,2026-08-28,"
                + "富时中国A50期货,198848\";";

        OvernightMarketQuote quote = ReflectionTestUtils.invokeMethod(client, "parse", "hf_CHA50CFD", response);

        assertNull(quote);
    }
}
