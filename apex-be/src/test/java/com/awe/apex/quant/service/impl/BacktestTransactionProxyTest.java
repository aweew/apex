package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.backtest.BacktestCostConfig;
import com.awe.apex.quant.backtest.BacktestRunContext;
import com.awe.apex.quant.domain.dto.BacktestRunReq;
import com.awe.apex.quant.domain.dto.BatchBacktestReq;
import com.awe.apex.quant.service.IBacktestService;
import com.awe.apex.quant.service.IConfigService;
import com.awe.apex.quant.strategy.Strategy;
import com.awe.apex.quant.strategy.StrategyParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BacktestTransactionProxyTest {

    private final IConfigService configService = mock(IConfigService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final TransactionStatus transactionStatus = mock(TransactionStatus.class);
    private final Strategy strategyS1 = mock(Strategy.class);
    private final Strategy strategyS2 = mock(Strategy.class);
    private final Strategy strategyS3 = mock(Strategy.class);
    private final StrategyParams strategyParams = mock(StrategyParams.class);
    private final BacktestServiceImpl target = spy(new BacktestServiceImpl());
    private IBacktestService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(target, "configService", configService);
        ReflectionTestUtils.setField(target, "strategies", List.of(strategyS1, strategyS2, strategyS3));
        ReflectionTestUtils.setField(target, "strategyParams", strategyParams);
        when(strategyS1.strategyId()).thenReturn("S1");
        when(strategyS2.strategyId()).thenReturn("S2");
        when(strategyS3.strategyId()).thenReturn("S3");
        when(strategyParams.s1FastMa()).thenReturn(20);
        when(strategyParams.s1SlowMa()).thenReturn(60);
        when(strategyParams.s1VolMa()).thenReturn(20);
        when(strategyParams.s2Ma()).thenReturn(60);
        when(strategyParams.s2RsiPeriod()).thenReturn(14);
        when(strategyParams.s2RsiOversold()).thenReturn(new java.math.BigDecimal("30"));
        when(strategyParams.s2RsiRebound()).thenReturn(new java.math.BigDecimal("35"));
        when(strategyParams.s2RsiOverbought()).thenReturn(new java.math.BigDecimal("70"));
        when(strategyParams.s3Lookback()).thenReturn(20);
        when(strategyParams.s3VolumeRatio()).thenReturn(new java.math.BigDecimal("1.5"));
        when(configService.getDecimal(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);

        TransactionInterceptor transactionInterceptor = new TransactionInterceptor();
        transactionInterceptor.setTransactionManager(transactionManager);
        transactionInterceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setTarget(target);
        proxyFactory.setInterfaces(IBacktestService.class);
        proxyFactory.addAdvice(transactionInterceptor);
        service = (IBacktestService) proxyFactory.getProxy();
    }

    @Test
    void shouldRollbackBatchTransactionWhenSystemFailureEscapes() {
        doThrow(new IllegalStateException("database unavailable")).when(target)
                .runWithCost(any(BacktestRunReq.class), any(BacktestCostConfig.class));
        BatchBacktestReq request = new BatchBacktestReq();
        request.setCodes(List.of("600519", "000001"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> service.batchRun(request));

        assertEquals("database unavailable", exception.getMessage());
        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(transactionStatus);
    }

    @Test
    void shouldRollbackStrategyComparisonWhenSystemFailureEscapes() {
        doThrow(new IllegalStateException("database unavailable")).when(target)
                .runComparedWithCost(any(BacktestRunReq.class), any(BacktestCostConfig.class),
                        any(BacktestRunContext.class));
        BacktestRunReq request = new BacktestRunReq();
        request.setCode("600519");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class, () -> service.compareStrategies(request));

        assertEquals("database unavailable", exception.getMessage());
        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(transactionStatus);
    }
}
