package com.awe.apex.quant.market;

import com.awe.apex.quant.domain.dto.OvernightMarketQuote;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalFuturesQuoteClientTest {

    @Test
    void parsesSinaFtseA50ContinuousFutureResponse() {
        GlobalFuturesQuoteClient client = new GlobalFuturesQuoteClient();
        String response = "var hq_str_hf_FTSE=\"富时 A50 期指连续,13520.0,86.5,0.64,13540.0,13410.0\";";

        OvernightMarketQuote quote = ReflectionTestUtils.invokeMethod(client, "parse", "hf_FTSE", response);

        assertEquals("hf_FTSE", quote.getSymbol());
        assertEquals("富时 A50 期指连续", quote.getName());
        assertEquals(new BigDecimal("13520.0"), quote.getLatestPrice());
        assertEquals(new BigDecimal("0.64"), quote.getPctChg());
    }
}
