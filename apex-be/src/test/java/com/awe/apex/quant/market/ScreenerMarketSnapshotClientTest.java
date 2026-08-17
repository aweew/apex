package com.awe.apex.quant.market;

import com.awe.apex.common.util.SpringUtils;
import com.awe.apex.quant.domain.dto.ScreenerMarketSnapshot;
import com.awe.apex.quant.domain.dto.ScreenerMarketSnapshotBatch;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScreenerMarketSnapshotClientTest {

    private static ApplicationContext originalApplicationContext;

    @BeforeAll
    static void setUpJsonUtils() {
        originalApplicationContext = SpringUtils.getApplicationContext();
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean(ObjectMapper.class)).thenReturn(new ObjectMapper().findAndRegisterModules());
        new SpringUtils().setApplicationContext(applicationContext);
    }

    @AfterAll
    static void restoreJsonUtils() {
        new SpringUtils().setApplicationContext(originalApplicationContext);
    }

    @Test
    void shouldParseRealtimeTurnoverVolumeRatioAndMarketValues() throws Exception {
        String body = """
                {"data":{"diff":[{
                  "f2":12.34,"f3":4.2,"f6":880000000,"f8":6.8,"f9":18.2,"f10":1.36,
                  "f12":"600001","f13":1,"f14":"示例股份","f20":12000000000,
                  "f21":8000000000,"f23":2.1,"f100":"电子","f124":1786982400
                }]}}
                """;

        ScreenerMarketSnapshotBatch batch = new ScreenerMarketSnapshotClient().parse(body);

        assertEquals(1, batch.getItems().size());
        ScreenerMarketSnapshot item = batch.getItems().get(0);
        assertEquals("SH", item.getMarket());
        assertEquals(new BigDecimal("6.8"), item.getTurnoverRate());
        assertEquals(new BigDecimal("1.36"), item.getVolumeRatio());
        assertEquals(new BigDecimal("12000000000"), item.getTotalMv());
        assertNotNull(batch.getAsOf());
    }
}
