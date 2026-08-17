package com.awe.apex.quant.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.WatchlistImportReq;
import com.awe.apex.quant.domain.entity.Watchlist;
import com.awe.apex.quant.mapper.WatchlistMapper;
import com.awe.apex.quant.service.IBarDailyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class WatchlistAsyncContextTest {

    @TempDir
    Path tempDir;

    @Test
    void importPreheatKeepsSubmittingUserAcrossExecutorBoundary() throws Exception {
        Path csvFile = tempDir.resolve("watchlist.csv");
        Files.writeString(csvFile, "代码,名称,市场\n600519,贵州茅台,SH\n");
        ApexUserContext userContext = new ApexUserContext();
        AtomicReference<Runnable> pendingTask = new AtomicReference<>();
        AtomicLong observedUserId = new AtomicLong();
        WatchlistMapper watchlistMapper = mock(WatchlistMapper.class);
        IBarDailyService barDailyService = mock(IBarDailyService.class);
        WatchlistServiceImpl service = spy(new WatchlistServiceImpl());
        when(watchlistMapper.selectOne(any())).thenReturn(null);
        when(watchlistMapper.insert(any(Watchlist.class))).thenReturn(1);
        doAnswer(invocation -> {
            observedUserId.set(userContext.currentUserId());
            return Map.of();
        }).when(service).fillQuotes(anyString(), anyInt(), anyInt());
        ReflectionTestUtils.setField(service, "baseMapper", watchlistMapper);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        ReflectionTestUtils.setField(service, "barDailyService", barDailyService);
        ReflectionTestUtils.setField(service, "preheatExecutor", (Executor) pendingTask::set);
        WatchlistImportReq request = new WatchlistImportReq();
        request.setFilePath(csvFile.toString());
        request.setGroupName("我的自选");

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(99L);

            userContext.runAsUser(7L, () -> service.importFromMxFile(request));
            assertNotNull(pendingTask.get());
            pendingTask.get().run();

            assertEquals(7L, observedUserId.get());
            assertEquals(99L, userContext.currentUserId());
        }
    }
}
