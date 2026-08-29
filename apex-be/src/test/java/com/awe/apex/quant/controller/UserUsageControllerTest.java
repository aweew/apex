package com.awe.apex.quant.controller;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.domain.dto.UserUsageOverviewResp;
import com.awe.apex.quant.service.ApexUserAuthService;
import com.awe.apex.quant.service.UserUsageService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserUsageControllerTest {

    @Test
    void overviewRequiresAdministratorPermission() {
        ApexUserAuthService userAuthService = mock(ApexUserAuthService.class);
        UserUsageService userUsageService = mock(UserUsageService.class);
        UserUsageController controller = new UserUsageController();
        UserUsageOverviewResp expected = UserUsageOverviewResp.builder().build();
        ReflectionTestUtils.setField(controller, "userAuthService", userAuthService);
        ReflectionTestUtils.setField(controller, "userUsageService", userUsageService);
        when(userUsageService.overview(30)).thenReturn(expected);

        UserUsageOverviewResp actual = controller.overview(30).getData();

        assertSame(expected, actual);
        verify(userAuthService).requireAdmin();
        verify(userUsageService).overview(30);
    }

    @Test
    void overviewDoesNotQueryStatisticsWhenAdministratorPermissionIsDenied() {
        ApexUserAuthService userAuthService = mock(ApexUserAuthService.class);
        UserUsageService userUsageService = mock(UserUsageService.class);
        UserUsageController controller = new UserUsageController();
        ReflectionTestUtils.setField(controller, "userAuthService", userAuthService);
        ReflectionTestUtils.setField(controller, "userUsageService", userUsageService);
        doThrow(new BusinessException("无管理员权限")).when(userAuthService).requireAdmin();

        assertThrows(BusinessException.class, () -> controller.overview(30));

        verify(userUsageService, never()).overview(30);
    }
}
