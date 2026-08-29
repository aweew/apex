package com.awe.apex.quant.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.domain.dto.UserActivityReq;
import com.awe.apex.quant.domain.dto.UserUsageModuleResp;
import com.awe.apex.quant.domain.dto.UserUsageOverviewResp;
import com.awe.apex.quant.domain.dto.UserUsageTrendResp;
import com.awe.apex.quant.domain.entity.ApexUserActivity;
import com.awe.apex.quant.mapper.UserUsageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserUsageServiceImplTest {

    private final UserUsageMapper userUsageMapper = mock(UserUsageMapper.class);
    private final UserUsageServiceImpl service = new UserUsageServiceImpl();

    @BeforeEach
    void setUp() {
        Mockito.reset(userUsageMapper);
        ReflectionTestUtils.setField(service, "userUsageMapper", userUsageMapper);
    }

    @Test
    void recordsWhitelistedPageViewForCurrentUser() {
        UserActivityReq request = new UserActivityReq();
        request.setModuleCode("DASHBOARD");

        try (MockedStatic<StpUtil> stpUtil = Mockito.mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            service.recordPageView(request);
        }

        ArgumentCaptor<ApexUserActivity> activityCaptor = ArgumentCaptor.forClass(ApexUserActivity.class);
        verify(userUsageMapper).insert(activityCaptor.capture());
        ApexUserActivity activity = activityCaptor.getValue();
        assertEquals(7L, activity.getUserId());
        assertEquals("PAGE_VIEW", activity.getActivityType());
        assertEquals("DASHBOARD", activity.getModuleCode());
        assertEquals("决策看板", activity.getModuleName());
    }

    @Test
    void rejectsUnknownModuleWithoutWritingActivity() {
        UserActivityReq request = new UserActivityReq();
        request.setModuleCode("UNKNOWN");

        assertThrows(BusinessException.class, () -> service.recordPageView(request));

        verify(userUsageMapper, never()).insert(isA(ApexUserActivity.class));
    }

    @Test
    void fillsMissingTrendDaysAndCalculatesModuleShare() {
        LocalDate today = LocalDate.now();
        UserUsageOverviewResp summary = UserUsageOverviewResp.builder()
                .totalUsers(4L)
                .enabledUsers(3L)
                .activeUsers(2L)
                .newUsers(1L)
                .totalVisits(8L)
                .build();
        UserUsageTrendResp todayTrend = UserUsageTrendResp.builder()
                .date(today)
                .activeUsers(2L)
                .visits(3L)
                .build();
        UserUsageModuleResp dashboard = UserUsageModuleResp.builder()
                .moduleCode("DASHBOARD")
                .moduleName("决策看板")
                .visits(3L)
                .activeUsers(2L)
                .build();
        UserUsageModuleResp market = UserUsageModuleResp.builder()
                .moduleCode("MARKET")
                .moduleName("行情中心")
                .visits(2L)
                .activeUsers(1L)
                .build();
        when(userUsageMapper.selectOverview(any())).thenReturn(summary);
        when(userUsageMapper.selectDailyTrend(any())).thenReturn(List.of(todayTrend));
        when(userUsageMapper.selectModuleUsage(any())).thenReturn(List.of(dashboard, market));
        when(userUsageMapper.selectUserUsage(any())).thenReturn(List.of());

        UserUsageOverviewResp overview = service.overview(7);

        assertEquals(7, overview.getPeriodDays());
        assertEquals(7, overview.getTrend().size());
        assertEquals(today.minusDays(6), overview.getTrend().get(0).getDate());
        assertEquals(0L, overview.getTrend().get(0).getVisits());
        assertEquals(3L, overview.getTrend().get(6).getVisits());
        assertEquals(50.0, overview.getActiveRate());
        assertEquals(60.0, overview.getModules().get(0).getVisitRate());
        assertEquals(40.0, overview.getModules().get(1).getVisitRate());
    }

    @Test
    void rejectsUnsupportedStatisticsPeriod() {
        assertThrows(BusinessException.class, () -> service.overview(0));
        assertThrows(BusinessException.class, () -> service.overview(91));
    }
}
