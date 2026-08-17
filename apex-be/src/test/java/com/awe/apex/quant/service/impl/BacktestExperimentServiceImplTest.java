package com.awe.apex.quant.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.domain.dto.BacktestCostResp;
import com.awe.apex.quant.domain.dto.BacktestExperimentDetailResp;
import com.awe.apex.quant.domain.dto.BacktestExperimentListResp;
import com.awe.apex.quant.domain.dto.RollingBacktestReq;
import com.awe.apex.quant.domain.dto.RollingBacktestResp;
import com.awe.apex.quant.domain.dto.RollingStrategyConfig;
import com.awe.apex.quant.domain.entity.BacktestExperiment;
import com.awe.apex.quant.mapper.BacktestExperimentMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BacktestExperimentServiceImplTest {

    private static final Long CURRENT_USER_ID = 7L;

    private static GenericApplicationContext applicationContext;
    private static ApplicationContext originalApplicationContext;

    private final BacktestExperimentMapper backtestExperimentMapper = mock(BacktestExperimentMapper.class);
    private final BacktestExperimentServiceImpl service = new BacktestExperimentServiceImpl();

    private MockedStatic<StpUtil> stpUtil;

    @BeforeAll
    static void initContext() {
        originalApplicationContext = SpringUtil.getApplicationContext();
        applicationContext = new GenericApplicationContext();
        applicationContext.registerBean(ObjectMapper.class, () -> new ObjectMapper().registerModule(new JavaTimeModule()));
        applicationContext.refresh();
        new SpringUtil().setApplicationContext(applicationContext);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, BacktestExperiment.class);
    }

    @AfterAll
    static void closeContext() {
        new SpringUtil().setApplicationContext(originalApplicationContext);
        applicationContext.close();
    }

    @BeforeEach
    void setUp() {
        stpUtil = mockStatic(StpUtil.class);
        stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(CURRENT_USER_ID);
        ReflectionTestUtils.setField(service, "backtestExperimentMapper", backtestExperimentMapper);
    }

    @AfterEach
    void tearDown() {
        stpUtil.close();
    }

    @Test
    void shouldSaveEffectiveRequestAndResultForCurrentUser() {
        doAnswer(invocation -> {
            BacktestExperiment experiment = invocation.getArgument(0);
            experiment.setId(101L);
            return 1;
        }).when(backtestExperimentMapper).insert(any(BacktestExperiment.class));

        Long experimentId = service.save(request(), result());

        ArgumentCaptor<BacktestExperiment> experimentCaptor = ArgumentCaptor.forClass(BacktestExperiment.class);
        verify(backtestExperimentMapper).insert(experimentCaptor.capture());
        BacktestExperiment experiment = experimentCaptor.getValue();
        assertEquals(101L, experimentId);
        assertEquals(CURRENT_USER_ID, experiment.getUserId());
        assertEquals("600519", experiment.getCode());
        assertEquals("fastMa=20, slowMa=60, volumeMa=20", experiment.getStrategyParameters());
        assertEquals(252, experiment.getTrainDays());
        assertEquals(63, experiment.getTestDays());
        assertEquals(63, experiment.getStepDays());
        assertEquals(new BigDecimal("1000000"), experiment.getInitCash());
        assertEquals(new BigDecimal("0.120000"), experiment.getCompoundedOutSampleReturn());
        assertEquals(new BigDecimal("0.000500"), experiment.getCommissionRate());
        assertEquals(new BigDecimal("0.000500"), experiment.getStampTaxRate());
        assertEquals(new BigDecimal("0.001000"), experiment.getBuySlippage());
        assertEquals(new BigDecimal("0.001000"), experiment.getSellSlippage());
        assertEquals("NEXT_OPEN_V3", experiment.getExecutionModelVersion());
        assertEquals("QFQ", experiment.getPriceAdjustment());
        assertTrue(experiment.getRequestJson().contains("\"trainDays\":252"));
        assertTrue(experiment.getRequestJson().contains("\"s1FastMa\":20"));
        assertTrue(experiment.getResultJson().contains("\"dataFingerprint\":\"fingerprint-1\""));
    }

    @Test
    void shouldRejectWhenExperimentCannotBePersisted() {
        when(backtestExperimentMapper.insert(any(BacktestExperiment.class))).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.save(request(), result()));

        assertEquals("回测实验保存失败", exception.getMessage());
    }

    @Test
    void shouldRejectExperimentWithoutEffectiveAssumptions() {
        RollingBacktestResp resultWithoutCost = result();
        resultWithoutCost.setCost(null);

        BusinessException costException = assertThrows(
                BusinessException.class, () -> service.save(request(), resultWithoutCost));
        RollingBacktestResp resultWithoutCash = result();
        resultWithoutCash.setInitCash(null);
        BusinessException cashException = assertThrows(
                BusinessException.class, () -> service.save(request(), resultWithoutCash));

        assertEquals("回测实验成本快照不能为空", costException.getMessage());
        assertEquals("回测实验初始资金快照无效", cashException.getMessage());
        verify(backtestExperimentMapper, never()).insert(any(BacktestExperiment.class));
    }

    @Test
    void shouldFilterRecentExperimentsByCurrentUser() {
        BacktestExperiment experiment = experiment();
        when(backtestExperimentMapper.selectList(any())).thenReturn(List.of(experiment));

        List<BacktestExperimentListResp> experiments = service.list(20);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<BacktestExperiment>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(backtestExperimentMapper).selectList(queryCaptor.capture());
        LambdaQueryWrapper<BacktestExperiment> query = (LambdaQueryWrapper<BacktestExperiment>) queryCaptor.getValue();
        assertTrue(query.getSqlSegment().contains("user_id"));
        assertTrue(query.getParamNameValuePairs().containsValue(CURRENT_USER_ID));
        assertTrue(query.getSqlSelect().contains("commission_rate"));
        assertTrue(query.getSqlSelect().contains("sell_slippage"));
        assertTrue(query.getSqlSelect().contains("init_cash"));
        assertTrue(query.getSqlSelect().contains("execution_model_version"));
        assertTrue(query.getSqlSelect().contains("price_adjustment"));
        assertFalse(query.getSqlSelect().contains("request_json"));
        assertFalse(query.getSqlSelect().contains("result_json"));
        assertEquals(101L, experiments.get(0).getId());
        assertEquals("600519", experiments.get(0).getCode());
        assertEquals("fastMa=20, slowMa=60, volumeMa=20", experiments.get(0).getStrategyParameters());
        assertEquals(252, experiments.get(0).getTrainDays());
        assertEquals(63, experiments.get(0).getTestDays());
        assertEquals(63, experiments.get(0).getStepDays());
        assertEquals(new BigDecimal("1000000"), experiments.get(0).getInitCash());
        assertEquals(LocalDate.of(2024, 1, 3), experiments.get(0).getOutSampleBeginDate());
        assertEquals(LocalDate.of(2026, 8, 14), experiments.get(0).getOutSampleEndDate());
        assertEquals(new BigDecimal("0.000500"), experiments.get(0).getCommissionRate());
        assertEquals(new BigDecimal("0.000500"), experiments.get(0).getStampTaxRate());
        assertEquals(new BigDecimal("0.001000"), experiments.get(0).getBuySlippage());
        assertEquals(new BigDecimal("0.001000"), experiments.get(0).getSellSlippage());
        assertEquals("NEXT_OPEN_V3", experiments.get(0).getExecutionModelVersion());
        assertEquals("QFQ", experiments.get(0).getPriceAdjustment());
    }

    @Test
    void shouldReturnEmptyHistoryWhenMapperHasNoRows() {
        when(backtestExperimentMapper.selectList(any())).thenReturn(null);

        List<BacktestExperimentListResp> experiments = service.list(20);

        assertTrue(experiments.isEmpty());
    }

    @Test
    void shouldParseOwnedExperimentDetail() {
        BacktestExperiment experiment = experiment();
        when(backtestExperimentMapper.selectOne(any())).thenReturn(experiment);

        BacktestExperimentDetailResp detail = service.detail(101L);

        assertEquals(101L, detail.getId());
        assertEquals("600519", detail.getRequest().getCode());
        assertEquals(20, detail.getRequest().getStrategyConfig().getS1FastMa());
        assertEquals(101L, detail.getResult().getExperimentId());
        assertEquals("fingerprint-1", detail.getResult().getDataFingerprint());
    }

    @Test
    void shouldRejectMissingOrUnownedExperimentBeforeDelete() {
        when(backtestExperimentMapper.selectOne(any())).thenReturn(null);

        BusinessException detailException = assertThrows(BusinessException.class, () -> service.detail(99L));
        BusinessException deleteException = assertThrows(BusinessException.class, () -> service.remove(99L));

        assertEquals("回测实验不存在", detailException.getMessage());
        assertEquals("回测实验不存在", deleteException.getMessage());
        verify(backtestExperimentMapper, never()).deleteById(any());
    }

    @Test
    void shouldDeleteExperimentOwnedByCurrentUser() {
        BacktestExperiment experiment = experiment();
        when(backtestExperimentMapper.selectOne(any())).thenReturn(experiment);

        service.remove(101L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<BacktestExperiment>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(backtestExperimentMapper).selectOne(queryCaptor.capture());
        LambdaQueryWrapper<BacktestExperiment> query = (LambdaQueryWrapper<BacktestExperiment>) queryCaptor.getValue();
        assertTrue(query.getSqlSegment().matches("(?s).*\\bid\\b.*"));
        assertTrue(query.getSqlSegment().contains("user_id"));
        assertTrue(query.getParamNameValuePairs().containsValue(CURRENT_USER_ID));
        verify(backtestExperimentMapper).deleteById(101L);
    }

    private RollingBacktestReq request() {
        RollingBacktestReq request = new RollingBacktestReq();
        request.setCode("600519");
        request.setStrategyId("S1");
        request.setStrategyConfig(RollingStrategyConfig.builder()
                .strategyId("S1")
                .s1FastMa(20)
                .s1SlowMa(60)
                .s1VolumeMa(20)
                .build());
        request.setBeginDate("2023-01-03");
        request.setEndDate("2026-08-14");
        request.setInitCash(new BigDecimal("1000000"));
        request.setBenchmarkCode("000300");
        request.setWindowMode("ROLLING");
        request.setTrainDays(252);
        request.setTestDays(63);
        request.setStepDays(63);
        request.setCommissionRate(new BigDecimal("0.0005"));
        request.setStampTaxRate(new BigDecimal("0.0005"));
        request.setBuySlippage(new BigDecimal("0.001"));
        request.setSellSlippage(new BigDecimal("0.001"));
        return request;
    }

    private RollingBacktestResp result() {
        return RollingBacktestResp.builder()
                .code("600519")
                .strategyId("S1")
                .strategyName("均线趋势")
                .strategyParameters("fastMa=20, slowMa=60, volumeMa=20")
                .benchmarkCode("000300")
                .windowMode("ROLLING")
                .dataBeginDate(LocalDate.of(2023, 1, 3))
                .dataEndDate(LocalDate.of(2026, 8, 14))
                .outSampleBeginDate(LocalDate.of(2024, 1, 3))
                .outSampleEndDate(LocalDate.of(2026, 8, 14))
                .trainDays(252)
                .testDays(63)
                .stepDays(63)
                .initCash(new BigDecimal("1000000"))
                .foldCount(8)
                .compoundedOutSampleReturn(new BigDecimal("0.120000"))
                .compoundedBenchmarkReturn(new BigDecimal("0.080000"))
                .compoundedExcessReturn(new BigDecimal("0.040000"))
                .outSampleSharpe(new BigDecimal("1.200000"))
                .worstOutSampleDrawdown(new BigDecimal("0.090000"))
                .executionModelVersion("NEXT_OPEN_V3")
                .priceAdjustment("QFQ")
                .dataFingerprint("fingerprint-1")
                .cost(BacktestCostResp.builder()
                        .commissionRate(new BigDecimal("0.000500"))
                        .stampTaxRate(new BigDecimal("0.000500"))
                        .buySlippage(new BigDecimal("0.001000"))
                        .sellSlippage(new BigDecimal("0.001000"))
                        .build())
                .folds(List.of())
                .build();
    }

    private BacktestExperiment experiment() {
        RollingBacktestReq request = request();
        RollingBacktestResp result = result();
        return BacktestExperiment.builder()
                .id(101L)
                .userId(CURRENT_USER_ID)
                .code("600519")
                .strategyId("S1")
                .strategyName("均线趋势")
                .strategyParameters(result.getStrategyParameters())
                .benchmarkCode("000300")
                .windowMode("ROLLING")
                .dataBeginDate(result.getDataBeginDate())
                .dataEndDate(result.getDataEndDate())
                .outSampleBeginDate(result.getOutSampleBeginDate())
                .outSampleEndDate(result.getOutSampleEndDate())
                .trainDays(result.getTrainDays())
                .testDays(result.getTestDays())
                .stepDays(result.getStepDays())
                .initCash(request.getInitCash())
                .foldCount(8)
                .compoundedOutSampleReturn(result.getCompoundedOutSampleReturn())
                .compoundedBenchmarkReturn(result.getCompoundedBenchmarkReturn())
                .compoundedExcessReturn(result.getCompoundedExcessReturn())
                .outSampleSharpe(result.getOutSampleSharpe())
                .worstOutSampleDrawdown(result.getWorstOutSampleDrawdown())
                .dataFingerprint(result.getDataFingerprint())
                .commissionRate(result.getCost().getCommissionRate())
                .stampTaxRate(result.getCost().getStampTaxRate())
                .buySlippage(result.getCost().getBuySlippage())
                .sellSlippage(result.getCost().getSellSlippage())
                .executionModelVersion(result.getExecutionModelVersion())
                .priceAdjustment(result.getPriceAdjustment())
                .requestJson(com.awe.apex.common.util.JsonUtils.toJsonString(request))
                .resultJson(com.awe.apex.common.util.JsonUtils.toJsonString(result))
                .createTime(LocalDateTime.of(2026, 8, 16, 18, 30))
                .updateTime(LocalDateTime.of(2026, 8, 16, 18, 30))
                .deleted(0)
                .build();
    }
}
