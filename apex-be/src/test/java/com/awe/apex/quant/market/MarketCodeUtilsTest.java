package com.awe.apex.quant.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 市场代码工具测试
 */
class MarketCodeUtilsTest {

    @Test
    void normalizeAndSecId() {
        assertEquals("600519", MarketCodeUtils.normalizeCode("SH.600519"));
        assertEquals("SH", MarketCodeUtils.resolveMarket("600519"));
        assertEquals("1.600519", MarketCodeUtils.toEastMoneySecId("600519"));
        assertEquals("0.000001", MarketCodeUtils.toEastMoneySecId("000001"));
        assertEquals("SZ", MarketCodeUtils.resolveMarket("000001"));
    }
}
