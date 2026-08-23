package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.DecisionRunReq;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import com.awe.apex.quant.domain.dto.SyncOverviewResp;
import com.awe.apex.quant.domain.dto.SyncStartReq;
import com.awe.apex.quant.domain.dto.SyncTaskDefResp;
import com.awe.apex.quant.domain.entity.SyncJob;
import com.awe.apex.quant.mapper.SyncJobMapper;
import com.awe.apex.quant.service.IConfigService;
import com.awe.apex.quant.service.IDecisionService;
import com.awe.apex.quant.service.TaskProgressListener;
import com.awe.apex.quant.service.ApexUserAuthService;
import com.awe.apex.quant.sync.SyncTaskRegistry;
import com.awe.apex.quant.sync.SyncJobLeaseService;
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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
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
    private final SyncJobLeaseService syncJobLeaseService = mock(SyncJobLeaseService.class);
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
        ReflectionTestUtils.setField(service, "syncJobLeaseService", syncJobLeaseService);
        when(userContext.currentUserId()).thenReturn(7L);
        when(syncJobLeaseService.tryAcquire(any(), any(), any())).thenReturn(true);
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
        when(decisionService.run(any(DecisionRunReq.class), any(TaskProgressListener.class)))
                .thenReturn(DecisionTodayResp.builder()
                .runNo("RUN-1").buyCount(0).sellCount(0).holdCount(0).message("完成").build());
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void manualDecisionRequiresAdminAndUsesSharedTaskRecord() {
        SyncStartReq request = new SyncStartReq();
        request.setTaskType("DECISION");

        service.start(request);

        assertEquals("DECISION", savedJob.get().getTaskType());
        verify(userAuthService).requireAdmin();
        verify(syncJobMapper, never()).selectOne(any());
    }

    @Test
    void perUserDecisionStartIsRejected() {
        SyncStartReq request = new SyncStartReq();
        request.setTaskType("DECISION");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.startForUser(request, 9L));

        assertTrue(exception.getMessage().contains("系统共享任务"));
        verify(syncJobMapper, never()).insert(any(SyncJob.class));
    }

    @Test
    void runningDecisionOnAnotherInstancePreventsDuplicateStart() {
        when(syncJobLeaseService.tryAcquire(any(), any(), any())).thenReturn(false);
        SyncStartReq request = new SyncStartReq();
        request.setTaskType("DECISION");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.startSystemTask(request));

        assertTrue(exception.getMessage().contains("其他服务实例"));
        verify(syncJobMapper, never()).insert(any(SyncJob.class));
    }

    @Test
    void sharedDecisionJobIsVisibleToAllUsers() {
        savedJob.set(SyncJob.builder().id(201L).taskType("DECISION").status("SUCCESS").build());

        assertEquals(201L, service.getJob(201L).getId());
    }

    @Test
    void pollingRunningDecisionJobDoesNotWriteStaleStatus() {
        savedJob.set(SyncJob.builder()
                .id(201L)
                .taskType("DECISION")
                .status("RUNNING")
                .progressPct(99)
                .build());

        assertEquals(99, service.getJob(201L).getProgressPct());

        verify(syncJobMapper, never()).updateById(any(SyncJob.class));
        verify(syncJobMapper, never()).update(any(SyncJob.class), any());
    }

    @Test
    void reportsRunningTaskByType() {
        when(syncJobMapper.selectOne(any())).thenReturn(SyncJob.builder()
                .id(301L)
                .taskType("CLOSE_BUNDLE")
                .status("RUNNING")
                .build());

        assertTrue(service.isTaskRunning("CLOSE_BUNDLE"));
    }

    @Test
    void reconcileKeepsRecentJobPossiblyOwnedByAnotherInstance() {
        SyncJob runningJob = SyncJob.builder()
                .id(202L)
                .taskType("DECISION")
                .status("RUNNING")
                .startedAt(LocalDateTime.now().minusMinutes(10))
                .build();
        when(syncJobMapper.selectList(any())).thenReturn(java.util.List.of(runningJob));

        service.reconcileOrphanJobs();

        assertEquals("RUNNING", runningJob.getStatus());
        verify(syncJobMapper, never()).updateById(any(SyncJob.class));
    }

    @Test
    void reconcileFailsJobPastTimeoutAndGracePeriod() {
        SyncJob runningJob = SyncJob.builder()
                .id(203L)
                .taskType("DECISION")
                .status("RUNNING")
                .startedAt(LocalDateTime.now().minusMinutes(36))
                .build();
        when(syncJobMapper.selectList(any())).thenReturn(java.util.List.of(runningJob));

        service.reconcileOrphanJobs();

        assertEquals("FAILED", runningJob.getStatus());
        assertEquals("任务超过运行时限（僵尸任务已清理）", runningJob.getMessage());
        assertNotNull(runningJob.getFinishedAt());
        verify(syncJobMapper).updateById(runningJob);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shutdownReleasesRegisteredTaskLease() {
        Map<Long, String> leaseKeys = (Map<Long, String>) ReflectionTestUtils.getField(service, "runningLeaseKeys");
        Map<Long, String> leaseOwners = (Map<Long, String>) ReflectionTestUtils.getField(service, "runningLeaseOwners");
        assertNotNull(leaseKeys);
        assertNotNull(leaseOwners);
        leaseKeys.put(901L, "apex:sync:lease:CLOSE_BUNDLE");
        leaseOwners.put(901L, "owner-901");

        service.shutdown();

        verify(syncJobLeaseService).release("apex:sync:lease:CLOSE_BUNDLE", "owner-901");
    }

    @Test
    @SuppressWarnings("unchecked")
    void losingLeaseCancelsTheCurrentTaskFuture() {
        Map<Long, AtomicBoolean> cancelFlags = (Map<Long, AtomicBoolean>) ReflectionTestUtils.getField(service, "cancelFlags");
        Map<Long, Future<?>> runningFutures = (Map<Long, Future<?>>) ReflectionTestUtils.getField(service, "runningFutures");
        Future<?> future = mock(Future.class);
        assertNotNull(cancelFlags);
        assertNotNull(runningFutures);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        cancelFlags.put(902L, cancelled);
        runningFutures.put(902L, future);

        ReflectionTestUtils.invokeMethod(service, "cancelSyncJobAfterLeaseLoss", 902L, "租约已失效");

        assertTrue(cancelled.get());
        verify(future).cancel(true);
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
    void overviewSelectsLatestSuccessByJobId() {
        when(syncJobMapper.selectList(any())).thenReturn(java.util.List.of());

        service.overview();

        ArgumentCaptor<Wrapper<SyncJob>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(syncJobMapper, atLeastOnce()).selectOne(queryCaptor.capture());
        java.util.List<String> successQueries = new java.util.ArrayList<>();
        for (Wrapper<SyncJob> query : queryCaptor.getAllValues()) {
            String sql = query.getSqlSegment();
            if (sql.contains("status")) {
                successQueries.add(sql);
            }
        }
        assertFalse(successQueries.isEmpty());
        assertTrue(successQueries.stream().allMatch(sql -> sql.contains("id")));
        assertTrue(successQueries.stream().noneMatch(sql -> sql.contains("finished_at")));
    }

    @Test
    void overviewTreatsPendingTaskAsRunning() {
        SyncJob pendingJob = SyncJob.builder()
                .id(301L)
                .taskType("CLOSE_BUNDLE")
                .taskName("一键收盘同步")
                .status("PENDING")
                .build();
        when(syncJobMapper.selectList(any())).thenReturn(java.util.List.of(pendingJob));

        SyncOverviewResp overview = service.overview();

        SyncTaskDefResp closeBundleTask = null;
        for (SyncTaskDefResp task : overview.getTasks()) {
            if ("CLOSE_BUNDLE".equals(task.getTaskType())) {
                closeBundleTask = task;
                break;
            }
        }
        assertNotNull(closeBundleTask);
        assertTrue(closeBundleTask.getRunning());
        assertEquals(1, overview.getRunningCount());
    }

    @Test
    void executorProjectsDecisionForEveryEnabledUser() throws Exception {
        ApexUserContext realUserContext = new ApexUserContext();
        ReflectionTestUtils.setField(service, "userContext", realUserContext);
        AtomicLong observedUserId = new AtomicLong();
        CountDownLatch decisionFinished = new CountDownLatch(1);
        when(userAuthService.listEnabledUserIds()).thenReturn(java.util.List.of(9L));
        when(decisionService.run(any(DecisionRunReq.class), any(TaskProgressListener.class))).thenAnswer(invocation -> {
            observedUserId.set(realUserContext.currentUserId());
            decisionFinished.countDown();
            return DecisionTodayResp.builder()
                    .runNo("RUN-2").buyCount(0).sellCount(0).holdCount(0).message("完成").build();
        });
        SyncStartReq request = new SyncStartReq();
        request.setTaskType("DECISION");

        service.startSystemTask(request);

        assertTrue(decisionFinished.await(2, TimeUnit.SECONDS));
        assertEquals(9L, observedUserId.get());
        verify(decisionService).refreshMarketSignals(any(DecisionRunReq.class), any(TaskProgressListener.class));
    }

    @Test
    void oneUserFailureDoesNotSkipRemainingUsers() throws Exception {
        ApexUserContext realUserContext = new ApexUserContext();
        ReflectionTestUtils.setField(service, "userContext", realUserContext);
        when(userAuthService.listEnabledUserIds()).thenReturn(java.util.List.of(9L, 10L));
        AtomicInteger executionCount = new AtomicInteger();
        CountDownLatch allUsersFinished = new CountDownLatch(1);
        when(decisionService.run(any(DecisionRunReq.class), any(TaskProgressListener.class))).thenAnswer(invocation -> {
            int currentExecution = executionCount.incrementAndGet();
            if (currentExecution == 1) {
                throw new BusinessException("用户组合不可用");
            }
            allUsersFinished.countDown();
            return DecisionTodayResp.builder()
                    .runNo("RUN-10").buyCount(0).sellCount(0).holdCount(0).message("完成").build();
        });
        SyncStartReq request = new SyncStartReq();
        request.setTaskType("DECISION");

        service.startSystemTask(request);

        assertTrue(allUsersFinished.await(2, TimeUnit.SECONDS));
        assertEquals(2, executionCount.get());
    }

    @Test
    void userDecisionsUseBoundedParallelism() throws Exception {
        ApexUserContext realUserContext = new ApexUserContext();
        ReflectionTestUtils.setField(service, "userContext", realUserContext);
        when(userAuthService.listEnabledUserIds()).thenReturn(java.util.List.of(1L, 2L, 3L, 4L));
        AtomicInteger activeCount = new AtomicInteger();
        AtomicInteger maxActiveCount = new AtomicInteger();
        CountDownLatch firstTwoStarted = new CountDownLatch(2);
        CountDownLatch releaseDecisions = new CountDownLatch(1);
        CountDownLatch allUsersFinished = new CountDownLatch(4);
        CopyOnWriteArrayList<Long> observedUserIds = new CopyOnWriteArrayList<>();
        when(decisionService.run(any(DecisionRunReq.class), any(TaskProgressListener.class))).thenAnswer(invocation -> {
            int currentActive = activeCount.incrementAndGet();
            maxActiveCount.accumulateAndGet(currentActive, Math::max);
            observedUserIds.add(realUserContext.currentUserId());
            firstTwoStarted.countDown();
            try {
                assertTrue(releaseDecisions.await(2, TimeUnit.SECONDS));
                return DecisionTodayResp.builder()
                        .runNo("RUN-" + realUserContext.currentUserId())
                        .buyCount(0).sellCount(0).holdCount(0).message("完成").build();
            } finally {
                activeCount.decrementAndGet();
                allUsersFinished.countDown();
            }
        });
        SyncStartReq request = new SyncStartReq();
        request.setTaskType("DECISION");

        service.startSystemTask(request);

        assertTrue(firstTwoStarted.await(2, TimeUnit.SECONDS));
        assertEquals(2, maxActiveCount.get());
        releaseDecisions.countDown();
        assertTrue(allUsersFinished.await(2, TimeUnit.SECONDS));
        assertEquals(java.util.Set.of(1L, 2L, 3L, 4L), new java.util.HashSet<>(observedUserIds));
    }

    @Test
    void decisionJobPersistsIntermediateProgressWhileRunning() throws Exception {
        CountDownLatch progressReported = new CountDownLatch(1);
        CountDownLatch releaseDecision = new CountDownLatch(1);
        when(decisionService.refreshMarketSignals(any(DecisionRunReq.class), any(TaskProgressListener.class)))
                .thenAnswer(invocation -> {
            TaskProgressListener progressListener = invocation.getArgument(1);
            progressListener.onProgress(40, 100, "正在扫描策略信号");
            progressReported.countDown();
            assertTrue(releaseDecision.await(2, TimeUnit.SECONDS));
            return null;
        });
        SyncStartReq request = new SyncStartReq();
        request.setTaskType("DECISION");

        try {
            service.start(request);

            assertTrue(progressReported.await(2, TimeUnit.SECONDS));
            assertEquals("RUNNING", savedJob.get().getStatus());
            assertEquals(28, savedJob.get().getProgressPct());
            assertEquals(40, savedJob.get().getDoneItems());
            assertEquals(100, savedJob.get().getTotalItems());
            assertEquals("正在扫描策略信号", savedJob.get().getMessage());
            assertTrue(service.isCurrentUserDecisionRunning());
        } finally {
            releaseDecision.countDown();
        }
    }
}
