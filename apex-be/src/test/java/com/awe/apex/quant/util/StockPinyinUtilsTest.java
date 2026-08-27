package com.awe.apex.quant.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StockPinyinUtilsTest {

    @Test
    void shouldBuildNormalizedStockNameAbbreviation() {
        assertEquals("gzmt", StockPinyinUtils.buildAbbr("贵州茅台"));
        assertEquals("stgzmt", StockPinyinUtils.buildAbbr("*ST贵州茅台"));
        assertEquals("gzmtu", StockPinyinUtils.buildAbbr("贵州茅台-U"));
    }

    @Test
    void shouldReturnNullForBlankStockName() {
        assertNull(StockPinyinUtils.buildAbbr("  "));
    }
}
