package com.awe.apex.quant.controller;

import com.awe.apex.quant.domain.dto.UniverseRefreshReq;
import com.awe.apex.quant.domain.dto.UniverseRefreshResp;
import com.awe.apex.quant.service.ApexUserAuthService;
import com.awe.apex.quant.service.IUniverseService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UniverseControllerTest {

    @Test
    void shouldRequireAdminBeforePublishingSharedUniverse() {
        ApexUserAuthService userAuthService = mock(ApexUserAuthService.class);
        IUniverseService universeService = mock(IUniverseService.class);
        UniverseController controller = new UniverseController();
        ReflectionTestUtils.setField(controller, "userAuthService", userAuthService);
        ReflectionTestUtils.setField(controller, "universeService", universeService);
        UniverseRefreshReq req = new UniverseRefreshReq();
        UniverseRefreshResp expected = UniverseRefreshResp.builder().batchNo("shared-batch").build();
        when(universeService.refresh(req)).thenReturn(expected);

        UniverseRefreshResp result = controller.refresh(req).getData();

        assertSame(expected, result);
        verify(userAuthService).requireAdmin();
        verify(universeService).refresh(req);
    }
}
