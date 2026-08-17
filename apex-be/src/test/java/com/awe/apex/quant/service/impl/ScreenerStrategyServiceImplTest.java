package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.ScreenerStrategyRuleSaveReq;
import com.awe.apex.quant.domain.dto.ScreenerStrategySaveReq;
import com.awe.apex.quant.domain.entity.ScreenerStrategy;
import com.awe.apex.quant.mapper.ScreenerStrategyMapper;
import com.awe.apex.quant.mapper.ScreenerStrategyRuleMapper;
import com.awe.apex.quant.screener.ScreenerStrategyTemplateRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScreenerStrategyServiceImplTest {

    private ScreenerStrategyServiceImpl service;
    private ApexUserContext userContext;
    private ScreenerStrategyMapper strategyMapper;

    @BeforeEach
    void setUp() {
        service = new ScreenerStrategyServiceImpl();
        userContext = mock(ApexUserContext.class);
        strategyMapper = mock(ScreenerStrategyMapper.class);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        ReflectionTestUtils.setField(service, "strategyMapper", strategyMapper);
        ReflectionTestUtils.setField(service, "ruleMapper", mock(ScreenerStrategyRuleMapper.class));
        ReflectionTestUtils.setField(service, "templateRegistry", new ScreenerStrategyTemplateRegistry());
    }

    @Test
    void shouldRejectStrategyReturnedForAnotherUser() {
        when(userContext.currentUserId()).thenReturn(7L);
        when(strategyMapper.selectOne(any())).thenReturn(ScreenerStrategy.builder()
                .id(11L)
                .userId(8L)
                .name("其他用户策略")
                .build());

        assertThrows(BusinessException.class, () -> service.detail(11L));
    }

    @Test
    void shouldRejectReversedRangeBeforeWriting() {
        ScreenerStrategySaveReq request = ScreenerStrategySaveReq.builder()
                .name("无效区间")
                .runMode("REALTIME")
                .rules(List.of(ScreenerStrategyRuleSaveReq.builder()
                        .ruleType("TOTAL_MV")
                        .operatorCode("BETWEEN")
                        .minValue(new BigDecimal("200"))
                        .maxValue(new BigDecimal("50"))
                        .build()))
                .build();

        assertThrows(BusinessException.class, () -> service.save(request));
    }
}
