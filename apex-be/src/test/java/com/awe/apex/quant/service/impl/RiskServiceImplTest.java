package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.RiskOverviewResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.PaperAccount;
import com.awe.apex.quant.domain.entity.PaperPosition;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.PaperAccountMapper;
import com.awe.apex.quant.mapper.PaperPositionMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.service.ApexUserAuthService;
import com.awe.apex.quant.domain.dto.RiskRuleUpdateReq;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class RiskServiceImplTest {

    @Test
    void checkBeforeOrderIncludesExistingPositionForNormalizedCodeInSingleStockLimit() {
        PaperAccountMapper accountMapper = mock(PaperAccountMapper.class);
        PaperPositionMapper positionMapper = mock(PaperPositionMapper.class);
        BarDailyMapper barMapper = mock(BarDailyMapper.class);
        StockBasicMapper stockMapper = mock(StockBasicMapper.class);

        RiskServiceImpl service = spy(new RiskServiceImpl());
        ReflectionTestUtils.setField(service, "paperAccountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "paperPositionMapper", positionMapper);
        ReflectionTestUtils.setField(service, "barDailyMapper", barMapper);
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockMapper);
        ApexUserContext userContext = mock(ApexUserContext.class);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        when(userContext.currentUserIdOrNull()).thenReturn(7L);

        when(accountMapper.selectById(1L)).thenReturn(PaperAccount.builder()
                .id(1L)
                .userId(7L)
                .cash(new BigDecimal("90000"))
                .build());
        doReturn(RiskOverviewResp.builder()
                .cash(new BigDecimal("90000"))
                .positionValue(new BigDecimal("10000"))
                .totalLimit(new BigDecimal("0.80"))
                .singleLimit(new BigDecimal("0.15"))
                .industryLimit(new BigDecimal("0.30"))
                .build()).when(service).overview(1L);
        when(positionMapper.selectList(any())).thenReturn(List.of(PaperPosition.builder()
                .accountId(1L)
                .code("SH.600519")
                .quantity(100)
                .build()));
        when(barMapper.selectOne(any())).thenReturn(BarDaily.builder()
                .code("SH.600519")
                .closePrice(new BigDecimal("100"))
                .build());
        when(stockMapper.selectOne(any())).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.checkBeforeOrder(1L, "600519", "BUY", 60, new BigDecimal("100")));

        assertEquals("下单后单票仓位将超限", error.getMessage());
    }

    @Test
    void checkBeforeOrderRejectsWhenExistingPositionHasNoLatestPrice() {
        PaperAccountMapper accountMapper = mock(PaperAccountMapper.class);
        PaperPositionMapper positionMapper = mock(PaperPositionMapper.class);
        BarDailyMapper barMapper = mock(BarDailyMapper.class);
        StockBasicMapper stockMapper = mock(StockBasicMapper.class);
        RiskServiceImpl service = spy(new RiskServiceImpl());
        ReflectionTestUtils.setField(service, "paperAccountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "paperPositionMapper", positionMapper);
        ReflectionTestUtils.setField(service, "barDailyMapper", barMapper);
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockMapper);
        ApexUserContext userContext = mock(ApexUserContext.class);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        when(userContext.currentUserIdOrNull()).thenReturn(7L);

        when(accountMapper.selectById(1L)).thenReturn(PaperAccount.builder()
                .id(1L).userId(7L).cash(new BigDecimal("90000")).build());
        doReturn(RiskOverviewResp.builder()
                .cash(new BigDecimal("90000"))
                .positionValue(new BigDecimal("10000"))
                .totalLimit(new BigDecimal("0.80"))
                .singleLimit(new BigDecimal("0.15"))
                .industryLimit(new BigDecimal("0.30"))
                .build()).when(service).overview(1L);
        when(positionMapper.selectList(any())).thenReturn(List.of(PaperPosition.builder()
                .accountId(1L).code("600519").quantity(100).build()));
        when(barMapper.selectOne(any())).thenReturn(null);
        when(stockMapper.selectOne(any())).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.checkBeforeOrder(1L, "600519", "BUY", 10, new BigDecimal("100")));

        assertEquals("持仓缺少最新价，无法校验单票仓位: 600519", error.getMessage());
    }

    @Test
    void overviewRejectsForeignAccount() {
        PaperAccountMapper accountMapper = mock(PaperAccountMapper.class);
        ApexUserContext userContext = mock(ApexUserContext.class);
        RiskServiceImpl service = new RiskServiceImpl();
        ReflectionTestUtils.setField(service, "paperAccountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        when(userContext.currentUserIdOrNull()).thenReturn(7L);
        when(accountMapper.selectById(22L)).thenReturn(PaperAccount.builder()
                .id(22L)
                .userId(8L)
                .build());

        assertThrows(BusinessException.class, () -> service.overview(22L));
    }

    @Test
    void overviewRejectsMissingUserContextBeforeReadingAccount() {
        PaperAccountMapper accountMapper = mock(PaperAccountMapper.class);
        ApexUserContext userContext = mock(ApexUserContext.class);
        RiskServiceImpl service = new RiskServiceImpl();
        ReflectionTestUtils.setField(service, "paperAccountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        when(userContext.currentUserIdOrNull()).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.overview(22L));

        assertEquals("未获取到当前用户", exception.getMessage());
        verifyNoInteractions(accountMapper);
    }

    @Test
    void updatingGlobalRiskRuleRequiresAdmin() {
        ApexUserAuthService userAuthService = mock(ApexUserAuthService.class);
        RiskServiceImpl service = new RiskServiceImpl();
        ReflectionTestUtils.setField(service, "userAuthService", userAuthService);
        doThrow(new BusinessException("需要管理员权限")).when(userAuthService).requireAdmin();
        RiskRuleUpdateReq request = new RiskRuleUpdateReq();
        request.setRuleKey("single_stock_limit");
        request.setRuleValue("0.10");

        assertThrows(BusinessException.class, () -> service.updateRule(request));
    }
}
