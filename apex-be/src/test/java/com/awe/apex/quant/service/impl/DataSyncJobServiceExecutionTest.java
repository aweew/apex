package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.config.ScriptDatabaseEnvironment;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.BarSyncResp;
import com.awe.apex.quant.domain.dto.SyncJobResp;
import com.awe.apex.quant.domain.dto.SyncStartReq;
import com.awe.apex.quant.domain.entity.SyncJob;
import com.awe.apex.quant.mapper.SyncJobMapper;
import com.awe.apex.quant.service.ApexUserAuthService;
import com.awe.apex.quant.service.IBarDailyService;
import com.awe.apex.quant.service.IConfigService;
import com.awe.apex.quant.service.IMarketBriefingService;
import com.awe.apex.quant.service.IMorningBriefingService;
import com.awe.apex.quant.service.IMyHoldingService;
import com.awe.apex.quant.service.IPortfolioService;
import com.awe.apex.quant.service.IWatchlistService;
import com.awe.apex.quant.sync.SyncTaskRegistry;
import com.awe.apex.quant.sync.SyncJobLeaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataSyncJobServiceExecutionTest {

    @TempDir
    Path scriptDir;

    private final SyncJobMapper syncJobMapper = mock(SyncJobMapper.class);
    private final IMarketBriefingService marketBriefingService = mock(IMarketBriefingService.class);
    private final IMorningBriefingService morningBriefingService = mock(IMorningBriefingService.class);
    private final IWatchlistService watchlistService = mock(IWatchlistService.class);
    private final IBarDailyService barDailyService = mock(IBarDailyService.class);
    private final IMyHoldingService myHoldingService = mock(IMyHoldingService.class);
    private final IPortfolioService portfolioService = mock(IPortfolioService.class);
    private final IConfigService configService = mock(IConfigService.class);
    private final ApexUserAuthService userAuthService = mock(ApexUserAuthService.class);
    private final SyncJobLeaseService syncJobLeaseService = mock(SyncJobLeaseService.class);
    private final ApexUserContext userContext = new ApexUserContext();
    private final DataSyncJobServiceImpl service = new DataSyncJobServiceImpl();
    private final ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
    private final AtomicReference<SyncJob> savedJob = new AtomicReference<>();
    private final CopyOnWriteArrayList<SyncJob> updates = new CopyOnWriteArrayList<>();
    private final AtomicLong jobSequence = new AtomicLong(100L);

    @BeforeEach
    void setUp() throws Exception {
        taskExecutor.setCorePoolSize(1);
        taskExecutor.setMaxPoolSize(1);
        taskExecutor.setQueueCapacity(10);
        taskExecutor.initialize();
        Path scriptRunner = scriptDir.resolve("test-python");
        Files.writeString(scriptRunner, "#!/bin/sh\nif [ \"$1\" = \"-u\" ]; then shift; fi\nexec /bin/sh \"$@\"\n");
        Files.writeString(scriptDir.resolve("sync_hot.py"), "exit 0\n");
        assertTrue(scriptRunner.toFile().setExecutable(true));

        ReflectionTestUtils.setField(service, "syncJobMapper", syncJobMapper);
        ReflectionTestUtils.setField(service, "syncTaskRegistry", new SyncTaskRegistry());
        ReflectionTestUtils.setField(service, "marketBriefingService", marketBriefingService);
        ReflectionTestUtils.setField(service, "morningBriefingService", morningBriefingService);
        ReflectionTestUtils.setField(service, "watchlistService", watchlistService);
        ReflectionTestUtils.setField(service, "barDailyService", barDailyService);
        ReflectionTestUtils.setField(service, "myHoldingService", myHoldingService);
        ReflectionTestUtils.setField(service, "portfolioService", portfolioService);
        ReflectionTestUtils.setField(service, "configService", configService);
        ReflectionTestUtils.setField(service, "userAuthService", userAuthService);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(service, "scriptDatabaseEnvironment", mock(ScriptDatabaseEnvironment.class));
        ReflectionTestUtils.setField(service, "syncJobLeaseService", syncJobLeaseService);
        ReflectionTestUtils.setField(service, "syncJobTaskExecutor", taskExecutor);
        ReflectionTestUtils.setField(service, "pythonCmd", scriptRunner.toString());
        ReflectionTestUtils.setField(service, "scriptDirConfig", scriptDir.toString());

        when(syncJobMapper.selectOne(any())).thenReturn(null);
        when(syncJobMapper.insert(any(SyncJob.class))).thenAnswer(invocation -> {
            SyncJob job = invocation.getArgument(0);
            job.setId(jobSequence.incrementAndGet());
            savedJob.set(copyJob(job));
            return 1;
        });
        when(syncJobMapper.selectById(any(Long.class))).thenAnswer(invocation -> copyJob(savedJob.get()));
        when(syncJobMapper.updateById(any(SyncJob.class))).thenAnswer(invocation -> {
            SyncJob job = copyJob(invocation.getArgument(0));
            savedJob.set(job);
            updates.add(job);
            return 1;
        });
        when(syncJobMapper.update(any(SyncJob.class), any())).thenAnswer(invocation -> {
            SyncJob currentJob = savedJob.get();
            if (Objects.isNull(currentJob) || !"RUNNING".equals(currentJob.getStatus())) {
                return 0;
            }
            SyncJob job = copyJob(invocation.getArgument(0));
            savedJob.set(job);
            updates.add(job);
            return 1;
        });
        when(configService.getString("auto_sync_group", "我的自选")).thenReturn("我的自选");
        when(userAuthService.listEnabledUserIds()).thenReturn(List.of());
        when(syncJobLeaseService.tryAcquire(any(), any(), any())).thenReturn(true);
        when(watchlistService.listWatchlistCodes(any())).thenReturn(List.of());
        when(myHoldingService.listHoldingCodes()).thenReturn(List.of());
        when(portfolioService.listActiveHoldingCodes()).thenReturn(List.of());
        when(myHoldingService.refreshQuotesForCodes(any(), eq(false))).thenReturn(Map.of("success", 1, "fail", 0));
        when(portfolioService.snapshotAll()).thenReturn(1);
        when(barDailyService.syncStaleCodes(any())).thenReturn(
                BarSyncResp.builder().successCount(1).failCount(0).build());
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
        taskExecutor.shutdown();
    }

    @Test
    void failedScriptSkipsCloseBundlePostProcessing() throws Exception {
        when(userAuthService.listEnabledUserIds()).thenReturn(List.of(7L));

        SyncJob result = runCloseBundle("exit 4\n");

        assertEquals("FAILED", result.getStatus());
        assertTrue(result.getMessage().startsWith("脚本退出码 "));
        verify(marketBriefingService).invalidateCache();
        verify(morningBriefingService).invalidateCache();
        verify(userAuthService, never()).listEnabledUserIds();
        verify(watchlistService, never()).listWatchlistCodes(any());
    }

    @Test
    void nightlyRepairWithCompletedAndFailedStepsMarksJobPartial() throws Exception {
        Files.writeString(scriptDir.resolve("sync_nightly_repair.py"),
                "echo '[NIGHTLY_REPAIR] 执行完成，成功步骤=company_profile,fundamentals，失败步骤=daily_bars'\nexit 1\n");
        SyncStartReq request = new SyncStartReq();
        request.setTaskType("NIGHTLY_REPAIR");

        service.startSystemTask(request);
        SyncJob result = waitForTerminal();

        assertEquals("PARTIAL", result.getStatus());
        assertEquals("完成，但部分条目失败（详见日志）", result.getMessage());
    }

    @Test
    void missingBarTaskWithRemainingGapMarksJobPartial() throws Exception {
        Files.writeString(scriptDir.resolve("sync_missing_bars.py"),
                "echo '未完成，成功数=3，失败数=0，剩余缺口=17'\nexit 1\n");
        SyncStartReq request = new SyncStartReq();
        request.setTaskType("A_SHARE_MISSING");

        service.startSystemTask(request);
        SyncJob result = waitForTerminal();

        assertEquals("PARTIAL", result.getStatus());
        assertEquals("完成，但部分条目失败（详见日志）", result.getMessage());
    }

    @Test
    void saturatedExecutorMarksQueuedJobFailedAndReleasesTheLease() throws Exception {
        ThreadPoolTaskExecutor saturatedExecutor = new ThreadPoolTaskExecutor();
        saturatedExecutor.setCorePoolSize(1);
        saturatedExecutor.setMaxPoolSize(1);
        saturatedExecutor.setQueueCapacity(0);
        saturatedExecutor.initialize();
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        saturatedExecutor.execute(() -> {
            workerStarted.countDown();
            try {
                releaseWorker.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(workerStarted.await(1, TimeUnit.SECONDS));
        ReflectionTestUtils.setField(service, "syncJobTaskExecutor", saturatedExecutor);
        SyncStartReq request = new SyncStartReq();
        request.setTaskType("HOT");

        try {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> service.startSystemTask(request));

            assertTrue(exception.getMessage().contains("同步执行队列繁忙"));
            assertEquals("FAILED", savedJob.get().getStatus());
            assertEquals("同步执行队列繁忙，请稍后重试", savedJob.get().getMessage());
            verify(syncJobLeaseService).release(anyString(), anyString());
        } finally {
            releaseWorker.countDown();
            saturatedExecutor.shutdown();
        }
    }

    @Test
    void nightlyRepairProgressIgnoresNestedScriptCounters() {
        SyncJob job = SyncJob.builder()
                .taskType("NIGHTLY_REPAIR")
                .progressPct(0)
                .build();

        ReflectionTestUtils.invokeMethod(service, "updateProgressFromLine",
                job, "[NIGHTLY_REPAIR] 步骤 2/3：company_profile", 10L);
        ReflectionTestUtils.invokeMethod(service, "updateProgressFromLine",
                job, "[1/300] 000001 OK", 11L);

        assertEquals(66, job.getProgressPct());
        assertEquals(2, job.getDoneItems());
        assertEquals(3, job.getTotalItems());
    }

    @Test
    void nightlyRepairInvalidatesMarketBriefingCache() {
        SyncJob job = SyncJob.builder().taskType("NIGHTLY_REPAIR").build();

        ReflectionTestUtils.invokeMethod(service, "invalidateMarketBriefingCache",
                job, "NIGHTLY_REPAIR");

        verify(marketBriefingService).invalidateCache();
    }

    @Test
    void closeBundlePostProcessingFailureMarksJobFailedAfterPersistingStage() throws Exception {
        when(userAuthService.listEnabledUserIds()).thenThrow(new BusinessException("用户库不可用"));

        SyncJob result = runCloseBundle("exit 0\n");

        assertEquals("FAILED", result.getStatus());
        assertTrue(result.getMessage().contains("读取启用用户失败"), result::toString);
        assertTrue(updates.stream().anyMatch(job -> "RUNNING".equals(job.getStatus())
                && job.getMessage().contains("收盘后处理")
                && job.getProgressPct() >= 90
                && job.getProgressPct() < 100));
        assertNotNull(result.getFinishedAt());
    }

    @Test
    void sharedPostProcessingFailureContinuesSnapshotsAndMarksJobPartial() throws Exception {
        when(userAuthService.listEnabledUserIds()).thenReturn(List.of(7L));
        when(watchlistService.listWatchlistCodes("我的自选")).thenReturn(List.of("600000"));
        when(myHoldingService.listHoldingCodes()).thenReturn(List.of("600001"));
        when(portfolioService.listActiveHoldingCodes()).thenReturn(List.of("600001"));
        when(myHoldingService.refreshQuotesForCodes(any(), eq(false))).thenReturn(Map.of("success", 1, "fail", 1));

        SyncJob result = runCloseBundle("exit 0\n");

        assertEquals("PARTIAL", result.getStatus());
        assertEquals(100, result.getProgressPct());
        assertEquals(3, result.getDoneItems());
        assertEquals(3, result.getTotalItems());
        assertTrue(result.getMessage().contains("共享行情刷新失败 1 项"), result::toString);
        assertTrue(result.getLogTail().contains("[错误] 收盘后处理失败：共享行情：共享行情刷新失败 1 项"),
                result::toString);
        assertTrue(result.getLogTail().contains("阶段=用户 7 · 组合快照完成"), result::toString);
        verify(myHoldingService).refreshQuotesForCodes(List.of("600000", "600001"), false);
        verify(portfolioService).snapshotAll();
        verify(barDailyService).syncStaleCodes(List.of("600000", "600001"));
        verify(myHoldingService, never()).refreshQuotes(false);
        verify(portfolioService, never()).refreshQuotesAll(false);
    }

    @Test
    void closeBundleDeduplicatesSharedMarketWorkAcrossUsers() throws Exception {
        when(userAuthService.listEnabledUserIds()).thenReturn(List.of(1L, 2L));
        when(watchlistService.listWatchlistCodes("我的自选")).thenAnswer(invocation ->
                Objects.equals(userContext.currentUserId(), 1L)
                        ? List.of("600000", "600001") : List.of("600001", "600002"));
        when(myHoldingService.listHoldingCodes()).thenAnswer(invocation ->
                Objects.equals(userContext.currentUserId(), 1L)
                        ? List.of("600003") : List.of("600003", "600004"));
        when(portfolioService.listActiveHoldingCodes()).thenAnswer(invocation ->
                Objects.equals(userContext.currentUserId(), 1L)
                        ? List.of("600003", "600005") : List.of("600004", "600006"));

        SyncJob result = runCloseBundle("exit 0\n");

        assertEquals("SUCCESS", result.getStatus());
        verify(myHoldingService).refreshQuotesForCodes(
                List.of("600000", "600001", "600003", "600005", "600002", "600004", "600006"), false);
        verify(barDailyService).syncStaleCodes(
                List.of("600000", "600001", "600003", "600002", "600004"));
        verify(portfolioService, org.mockito.Mockito.times(2)).snapshotAll();
        verify(myHoldingService, never()).refreshQuotes(false);
        verify(portfolioService, never()).refreshQuotesAll(false);
        verify(barDailyService, never()).syncStaleWatchlist(any(), any());
    }

    @Test
    void turnoverItemFailureFinishesAsPartialInsteadOfGenericFailure() throws Exception {
        Files.writeString(scriptDir.resolve("backfill_turnover.py"),
                "echo '完成，成功数=49，失败数=1，更新数=1200'\nexit 1\n");
        SyncStartReq request = new SyncStartReq();
        request.setTaskType("TURNOVER");

        service.startSystemTask(request);
        SyncJob result = waitForTerminal();

        assertEquals("PARTIAL", result.getStatus());
        assertEquals(100, result.getProgressPct());
        assertTrue(result.getMessage().contains("部分条目失败"), result::toString);
    }

    @Test
    void processOutputIsPersistedWhileScriptIsRunning() throws Exception {
        Path script = scriptDir.resolve("sync_close_bundle.py");
        Path releaseFile = scriptDir.resolve("sync_close_bundle.py.release");
        Files.writeString(script, "echo '[CLOSE_BUNDLE] 步骤 1/5：index'\n"
                + "while [ ! -f \"${0}.release\" ]; do sleep 0.02; done\n"
                + "exit 3\n");
        SyncStartReq request = new SyncStartReq();
        request.setTaskType("CLOSE_BUNDLE");

        service.startSystemTask(request);
        SyncJob runningJob = null;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            SyncJob currentJob = savedJob.get();
            if (Objects.nonNull(currentJob)
                    && "RUNNING".equals(currentJob.getStatus())
                    && StringUtils.isNotBlank(currentJob.getLogTail())
                    && currentJob.getLogTail().contains("步骤 1/5：index")) {
                runningJob = currentJob;
                break;
            }
            Thread.sleep(10L);
        }
        Files.writeString(releaseFile, "continue\n");

        assertNotNull(runningJob, () -> String.valueOf(savedJob.get()));
        SyncJob result = waitForTerminal();
        assertEquals("FAILED", result.getStatus());
        assertTrue(result.getLogTail().contains("步骤 1/5：index"), result::toString);
        verify(syncJobMapper, atLeastOnce()).update(any(SyncJob.class), any());
    }

    @Test
    void cancellingPostProcessingStopsRemainingUserStages() throws Exception {
        CountDownLatch sharedQuoteStarted = new CountDownLatch(1);
        CountDownLatch releaseSharedQuote = new CountDownLatch(1);
        when(userAuthService.listEnabledUserIds()).thenReturn(List.of(7L));
        when(watchlistService.listWatchlistCodes("我的自选")).thenReturn(List.of("600000"));
        when(myHoldingService.refreshQuotesForCodes(any(), eq(false))).thenAnswer(invocation -> {
            sharedQuoteStarted.countDown();
            try {
                releaseSharedQuote.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new BusinessException("后处理被中断");
            }
            return Map.of("success", 1, "fail", 0);
        });
        Files.writeString(scriptDir.resolve("sync_close_bundle.py"), "exit 0\n");
        SyncStartReq request = new SyncStartReq();
        request.setTaskType("CLOSE_BUNDLE");

        SyncJobResp started = service.startSystemTask(request);
        assertTrue(sharedQuoteStarted.await(2, TimeUnit.SECONDS), () -> String.valueOf(savedJob.get()));
        service.stop(started.getId());
        releaseSharedQuote.countDown();
        SyncJob result = waitForTerminal();

        assertEquals("CANCELLED", result.getStatus());
        verify(portfolioService, never()).snapshotAll();
        verify(barDailyService, never()).syncStaleCodes(any());
    }

    @Test
    void shutdownDuringPostProcessingCancelsJobAndReleasesLeaseOnce() throws Exception {
        CountDownLatch sharedQuoteStarted = new CountDownLatch(1);
        when(userAuthService.listEnabledUserIds()).thenReturn(List.of(7L));
        when(watchlistService.listWatchlistCodes("我的自选")).thenReturn(List.of("600000"));
        when(myHoldingService.refreshQuotesForCodes(any(), eq(false))).thenAnswer(invocation -> {
            sharedQuoteStarted.countDown();
            new CountDownLatch(1).await();
            return Map.of("success", 1, "fail", 0);
        });
        Files.writeString(scriptDir.resolve("sync_close_bundle.py"), "exit 0\n");
        SyncStartReq request = new SyncStartReq();
        request.setTaskType("CLOSE_BUNDLE");

        service.startSystemTask(request);
        assertTrue(sharedQuoteStarted.await(2, TimeUnit.SECONDS), () -> String.valueOf(savedJob.get()));
        service.shutdown();
        SyncJob result = waitForTerminal();

        assertEquals("CANCELLED", result.getStatus());
        assertTrue(result.getMessage().contains("已取消"), result::toString);
        verify(syncJobLeaseService, times(1))
                .release(eq("apex:sync:lease:CLOSE_BUNDLE"), anyString());
    }

    @Test
    void progressPollingReloadsTerminalJobWhenRunningUpdateLosesRace() throws Exception {
        Path progressDir = scriptDir.resolve(".progress");
        Files.createDirectories(progressDir);
        Files.writeString(progressDir.resolve("bars_progress.json"), "{\"000001\":{\"ok\":true}}");
        SyncJob runningJob = SyncJob.builder()
                .id(701L)
                .taskType("A_SHARE_BARS")
                .status("RUNNING")
                .progressPct(99)
                .build();
        SyncJob successJob = SyncJob.builder()
                .id(701L)
                .taskType("A_SHARE_BARS")
                .status("SUCCESS")
                .progressPct(100)
                .build();
        when(syncJobMapper.selectById(701L)).thenReturn(runningJob, successJob);
        when(syncJobMapper.update(any(SyncJob.class), any())).thenReturn(0);

        SyncJobResp response = service.getJob(701L);

        assertEquals("SUCCESS", response.getStatus());
        assertEquals(100, response.getProgressPct());
        verify(syncJobMapper).update(any(SyncJob.class), any());
    }

    private SyncJob runCloseBundle(String scriptContent) throws Exception {
        Files.writeString(scriptDir.resolve("sync_close_bundle.py"), scriptContent);
        SyncStartReq request = new SyncStartReq();
        request.setTaskType("CLOSE_BUNDLE");
        service.startSystemTask(request);
        return waitForTerminal();
    }

    private SyncJob waitForTerminal() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            SyncJob job = savedJob.get();
            if (Objects.nonNull(job) && List.of("SUCCESS", "PARTIAL", "FAILED", "CANCELLED").contains(job.getStatus())) {
                return job;
            }
            Thread.sleep(10L);
        }
        throw new AssertionError("同步任务未在 5 秒内结束");
    }

    private SyncJob copyJob(SyncJob source) {
        if (Objects.isNull(source)) {
            return null;
        }
        return SyncJob.builder()
                .id(source.getId())
                .taskType(source.getTaskType())
                .taskName(source.getTaskName())
                .status(source.getStatus())
                .paramsJson(source.getParamsJson())
                .progressPct(source.getProgressPct())
                .doneItems(source.getDoneItems())
                .totalItems(source.getTotalItems())
                .message(source.getMessage())
                .logTail(source.getLogTail())
                .exitCode(source.getExitCode())
                .pid(source.getPid())
                .startedAt(source.getStartedAt())
                .finishedAt(source.getFinishedAt())
                .createTime(source.getCreateTime())
                .updateTime(LocalDateTime.now())
                .deleted(source.getDeleted())
                .build();
    }
}
