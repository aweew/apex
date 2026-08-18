package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.ScreenerStrategyRuleSaveReq;
import com.awe.apex.quant.domain.dto.ScreenerStrategyResp;
import com.awe.apex.quant.domain.dto.ScreenerStrategySaveReq;
import com.awe.apex.quant.domain.entity.ScreenerStrategy;
import com.awe.apex.quant.domain.entity.ScreenerStrategyRule;
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
    private ScreenerStrategyRuleMapper ruleMapper;

    @BeforeEach
    void setUp() {
        service = new ScreenerStrategyServiceImpl();
        userContext = mock(ApexUserContext.class);
        strategyMapper = mock(ScreenerStrategyMapper.class);
        ruleMapper = mock(ScreenerStrategyRuleMapper.class);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        ReflectionTestUtils.setField(service, "strategyMapper", strategyMapper);
        ReflectionTestUtils.setField(service, "ruleMapper", ruleMapper);
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

    @Test
    void shouldBuildReadableSummariesForCopiedTemplateRules() {
        when(userContext.currentUserId()).thenReturn(7L);
        when(strategyMapper.selectOne(any())).thenReturn(ScreenerStrategy.builder()
                .id(11L)
                .userId(7L)
                .name("公开首板分散模型 副本")
                .enabled(1)
                .build());
        when(ruleMapper.selectList(any())).thenReturn(List.of(
                ScreenerStrategyRule.builder()
                        .id(1L)
                        .strategyId(11L)
                        .ruleType("MARKET_BOARD")
                        .operatorCode("EQ")
                        .textValue("MAIN_BOARD")
                        .sortNo(10)
                        .build(),
                ScreenerStrategyRule.builder()
                        .id(2L)
                        .strategyId(11L)
                        .ruleType("FIRST_SEAL_TIME")
                        .operatorCode("LTE")
                        .textValue("103000")
                        .sortNo(20)
                        .build(),
                ScreenerStrategyRule.builder()
                        .id(3L)
                        .strategyId(11L)
                        .ruleType("TOTAL_MV")
                        .operatorCode("BETWEEN")
                        .minValue(new BigDecimal("5000000000"))
                        .maxValue(new BigDecimal("20000000000"))
                        .sortNo(30)
                        .build()));

        ScreenerStrategyResp result = service.detail(11L);

        org.junit.jupiter.api.Assertions.assertEquals("市场范围 沪深主板", result.getRules().get(0).getSummary());
        org.junit.jupiter.api.Assertions.assertEquals("首次封板时间 小于等于 10:30", result.getRules().get(1).getSummary());
        org.junit.jupiter.api.Assertions.assertEquals("总市值 50亿 - 200亿", result.getRules().get(2).getSummary());
    }
}
