package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import com.awe.apex.quant.domain.dto.SyncStartReq;
import com.awe.apex.quant.domain.entity.SyncJob;
import com.awe.apex.quant.mapper.SyncJobMapper;
import com.awe.apex.quant.service.IConfigService;
import com.awe.apex.quant.service.IDecisionService;
import com.awe.apex.quant.service.ApexUserAuthService;
import com.awe.apex.quant.sync.SyncTaskRegistry;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

class DataSyncJobServiceOwnershipTest {

    private final SyncJobMapper syncJobMapper = mock(SyncJobMapper.class);
    private final IDecisionService decisionService = mock(IDecisionService.class);
    private final IConfigService configService = mock(IConfigService.class);
    private final ApexUserAuthService userAuthService = mock(ApexUserAuthService.class);
    private final ApexUserContext userContext = mock(ApexUserContext.class);
    private final DataSyncJobServiceImpl service = new DataSyncJobServiceImpl();
    private final AtomicReference<SyncJob> savedJob = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SyncJob.class);
        ReflectionTestUtils.setField(service, "syncJobMapper", syncJobMapper);
        ReflectionTestUtils.setField(service, "syncTaskRegistry", new SyncTaskRegistry());
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(service, "decisionService", decisionService);
        ReflectionTestUtils.setField(service, "configService", configService);
        ReflectionTestUtils.setField(service, "userAuthService", userAuthService);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        when(userContext.currentUserId()).thenReturn(7L);
        when(syncJobMapper.selectOne(any())).thenReturn(null);
        when(syncJobMapper.insert(any(SyncJob.class))).thenAnswer(invocation -> {
            SyncJob job = invocation.getArgument(0);
            job.setId(101L);
            savedJob.set(job);
            return 1;
        });
        when(syncJobMapper.selectById(any(Long.class))).thenAnswer(invocation -> savedJob.get());
        when(syncJobMapper.updateById(any(SyncJob.class))).thenReturn(1);
        when(configService.getString("auto_sync_group", "我的自选")).thenReturn("我的自选");
        when(decisionService.run(any())).thenReturn(DecisionTodayResp.builder()
                .runNo("RUN-1").buyCount(0).sellCount(0).holdCount(0).message("完成").build());
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void manualDecisionDoesNotStoreUserAndUsesSharedTaskRecord() {
        SyncStartReq request = new SyncStartReq();
        request.setTaskType("DECISION");

        service.start(request);

        assertEquals("DECISION", savedJob.get().getTaskType());
        verify(syncJobMapper, never()).selectOne(any());
    }

    @Test
    void scheduledDecisionUsesExplicitRuntimeUserWithoutPersistingOwner() {
        SyncStartReq request = new SyncStartReq();
        request.setTaskType("DECISION");

        service.startForUser(request, 9L);

        assertEquals("DECISION", savedJob.get().getTaskType());
    }

    @Test
    void sharedDecisionJobIsVisibleToAllUsers() {
        savedJob.set(SyncJob.builder().id(201L).taskType("DECISION").status("SUCCESS").build());

        assertEquals(201L, service.getJob(201L).getId());
    }

    @Test
    void completedSharedDecisionJobCanBeReadWithoutUserCheck() {
        savedJob.set(SyncJob.builder().id(201L).taskType("DECISION").status("SUCCESS").build());

        assertEquals(201L, service.stop(201L).getId());
    }

    @Test
    void sharedMarketJobRemainsVisible() {
        savedJob.set(SyncJob.builder().id(202L).taskType("INDEX").status("SUCCESS").build());

        assertEquals(202L, service.getJob(202L).getId());
    }

    @Test
    void sharedMarketJobStartRequiresAdmin() {
        doThrow(new BusinessException("需要管理员权限")).when(userAuthService).requireAdmin();
        SyncStartReq request = new SyncStartReq();
        request.setTaskType("INDEX");

        assertThrows(BusinessException.class, () -> service.start(request));

        verify(syncJobMapper, never()).insert(any(SyncJob.class));
    }

    @Test
    void sharedMarketJobStopRequiresAdmin() {
        savedJob.set(SyncJob.builder().id(202L).taskType("INDEX").status("RUNNING").build());
        doThrow(new BusinessException("需要管理员权限")).when(userAuthService).requireAdmin();

        assertThrows(BusinessException.class, () -> service.stop(202L));
    }

    @Test
    void recentJobsContainsAllSharedTasks() {
        when(syncJobMapper.selectList(any())).thenReturn(java.util.List.of());

        service.recentJobs(20);

        ArgumentCaptor<Wrapper<SyncJob>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(syncJobMapper).selectList(queryCaptor.capture());
        Wrapper<SyncJob> query = queryCaptor.getValue();
        assertTrue(query.getSqlSegment().contains("id"));
    }

    @Test
    void executorRunsDecisionUnderStoredOwner() throws Exception {
        ApexUserContext realUserContext = new ApexUserContext();
        ReflectionTestUtils.setField(service, "userContext", realUserContext);
        AtomicLong observedUserId = new AtomicLong();
        CountDownLatch decisionFinished = new CountDownLatch(1);
        when(decisionService.run(any())).thenAnswer(invocation -> {
            observedUserId.set(realUserContext.currentUserId());
            decisionFinished.countDown();
            return DecisionTodayResp.builder()
                    .runNo("RUN-2").buyCount(0).sellCount(0).holdCount(0).message("完成").build();
        });
        SyncStartReq request = new SyncStartReq();
        request.setTaskType("DECISION");

        service.startForUser(request, 9L);

        assertTrue(decisionFinished.await(2, TimeUnit.SECONDS));
        assertEquals(9L, observedUserId.get());
    }
}
