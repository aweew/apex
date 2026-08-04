package com.awe.apex.quant.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 市场代码工具补充测试
 */
class MarketCodeUtilsMoreTest {

    @Test
    void normalizeAndMarket() {
        assertEquals("600519", MarketCodeUtils.normalizeCode("sh600519"));
        assertEquals("000001", MarketCodeUtils.normalizeCode("000001.SZ"));
        assertEquals("SH", MarketCodeUtils.resolveMarket("600519"));
        assertEquals("SZ", MarketCodeUtils.resolveMarket("000001"));
        assertEquals("BJ", MarketCodeUtils.resolveMarket("830799"));
        assertEquals("1.600519", MarketCodeUtils.toEastMoneySecId("600519"));
        assertEquals("0.000001", MarketCodeUtils.toEastMoneySecId("000001"));
        assertNull(MarketCodeUtils.normalizeCode(" "));
        assertTrue(MarketCodeUtils.isIndex("000300"));
        assertEquals("SH", MarketCodeUtils.resolveMarket("000300"));
        assertEquals("1.000300", MarketCodeUtils.toEastMoneySecId("000300"));
        assertEquals("BJ", MarketCodeUtils.resolveMarket("920178"));
        assertEquals("0.920178", MarketCodeUtils.toEastMoneySecId("920178"));
        // 沪市基金/科创板 ETF 不得误判北交所/深市
        assertEquals("SH", MarketCodeUtils.resolveMarket("510310"));
        assertEquals("SH", MarketCodeUtils.resolveMarket("515880"));
        assertEquals("SH", MarketCodeUtils.resolveMarket("588710"));
        assertEquals("SH", MarketCodeUtils.resolveMarket("588790"));
        assertEquals("SZ", MarketCodeUtils.resolveMarket("159952"));
        assertTrue(MarketCodeUtils.isFundOrEtf("515880"));
        assertTrue(MarketCodeUtils.isFundOrEtf("588710"));
        assertTrue(MarketCodeUtils.isFundOrEtf("159952"));
        assertFalse(MarketCodeUtils.isFundOrEtf("300442"));
    }
}
