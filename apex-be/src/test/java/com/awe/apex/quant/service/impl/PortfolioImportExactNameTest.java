package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioImportExactNameTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), StockBasic.class);
    }

    @Test
    void rejectsPartialStockNameInsteadOfFuzzyMatchingAnotherSecurity() {
        StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
        when(stockBasicMapper.selectOne(any())).thenReturn(null,
                StockBasic.builder().code("600519").name("贵州茅台").build());
        PortfolioServiceImpl service = new PortfolioServiceImpl();
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockBasicMapper);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "parseImportLine", "茅台,100,1488"));

        assertEquals("无法识别代码/名称: 茅台", exception.getMessage());
        verify(stockBasicMapper, times(1)).selectOne(any());
    }
}
