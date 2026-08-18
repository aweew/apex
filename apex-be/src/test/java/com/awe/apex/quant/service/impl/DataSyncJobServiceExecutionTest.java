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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    private final DataSyncJobServiceImpl service = new DataSyncJobServiceImpl();
    private final AtomicReference<SyncJob> savedJob = new AtomicReference<>();
    private final CopyOnWriteArrayList<SyncJob> updates = new CopyOnWriteArrayList<>();
    private final AtomicLong jobSequence = new AtomicLong(100L);

    @BeforeEach
    void setUp() throws Exception {
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
        ReflectionTestUtils.setField(service, "userContext", new ApexUserContext());
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(service, "scriptDatabaseEnvironment", mock(ScriptDatabaseEnvironment.class));
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
        when(configService.getString("auto_sync_group", "我的自选")).thenReturn("我的自选");
        when(userAuthService.listEnabledUserIds()).thenReturn(List.of());
        when(watchlistService.refreshQuotes(any(), any(), any())).thenReturn(Map.of("successCount", 1));
        when(myHoldingService.refreshQuotes(false)).thenReturn(Map.of("successCount", 1));
        when(portfolioService.refreshQuotesAll(false)).thenReturn(
                Map.of("portfolioCount", 1, "success", 1, "fail", 0));
        when(portfolioService.snapshotAll()).thenReturn(1);
        when(barDailyService.syncStaleWatchlist(any(), any())).thenReturn(
                BarSyncResp.builder().successCount(1).failCount(0).build());
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
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
        verify(watchlistService, never()).refreshQuotes(any(), any(), any());
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
    void reportedPostProcessingFailureContinuesRemainingStagesAndMarksJobFailed() throws Exception {
        when(userAuthService.listEnabledUserIds()).thenReturn(List.of(7L));
        when(myHoldingService.refreshQuotes(false)).thenReturn(Map.of("fail", 11, "barFail", 0));

        SyncJob result = runCloseBundle("exit 0\n");

        assertEquals("FAILED", result.getStatus());
        assertEquals(99, result.getProgressPct());
        assertEquals(4, result.getDoneItems());
        assertEquals(4, result.getTotalItems());
        assertTrue(result.getMessage().contains("持仓行情刷新失败 11 项"), result::toString);
        assertTrue(result.getLogTail().contains("[错误] 收盘后处理失败：用户 7 · 持仓行情：持仓行情刷新失败 11 项"),
                result::toString);
        assertTrue(result.getLogTail().contains("阶段=自选日线完成"), result::toString);
        verify(portfolioService).refreshQuotesAll(false);
        verify(portfolioService).snapshotAll();
        verify(barDailyService).syncStaleWatchlist("我的自选", 80);
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
    }

    @Test
    void cancellingPostProcessingStopsRemainingUserStages() throws Exception {
        CountDownLatch watchlistStarted = new CountDownLatch(1);
        CountDownLatch releaseWatchlist = new CountDownLatch(1);
        when(userAuthService.listEnabledUserIds()).thenReturn(List.of(7L));
        when(watchlistService.refreshQuotes(eq("我的自选"), eq(80), eq(false))).thenAnswer(invocation -> {
            watchlistStarted.countDown();
            try {
                releaseWatchlist.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new BusinessException("后处理被中断");
            }
            return Map.of("successCount", 1);
        });
        Files.writeString(scriptDir.resolve("sync_close_bundle.py"), "exit 0\n");
        SyncStartReq request = new SyncStartReq();
        request.setTaskType("CLOSE_BUNDLE");

        SyncJobResp started = service.startSystemTask(request);
        assertTrue(watchlistStarted.await(2, TimeUnit.SECONDS), () -> String.valueOf(savedJob.get()));
        service.stop(started.getId());
        releaseWatchlist.countDown();
        SyncJob result = waitForTerminal();

        assertEquals("CANCELLED", result.getStatus());
        verify(myHoldingService, never()).refreshQuotes(false);
        verify(portfolioService, never()).refreshQuotesAll(false);
        verify(barDailyService, never()).syncStaleWatchlist(any(), any());
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
            if (Objects.nonNull(job) && List.of("SUCCESS", "FAILED", "CANCELLED").contains(job.getStatus())) {
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
