package com.awe.apex.quant.controller;

import com.awe.apex.quant.domain.dto.CapitalFlowOverviewResp;
import com.awe.apex.quant.service.ApexUserAuthService;
import com.awe.apex.quant.service.ICapitalFlowService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapitalFlowControllerTest {

    @Test
    void refreshRequiresAdminAndReturnsLatestOverview() {
        ApexUserAuthService userAuthService = mock(ApexUserAuthService.class);
        ICapitalFlowService capitalFlowService = mock(ICapitalFlowService.class);
        CapitalFlowController controller = new CapitalFlowController();
        CapitalFlowOverviewResp expected = CapitalFlowOverviewResp.builder().build();
        ReflectionTestUtils.setField(controller, "userAuthService", userAuthService);
        ReflectionTestUtils.setField(controller, "capitalFlowService", capitalFlowService);
        when(capitalFlowService.refresh("all", 20)).thenReturn(expected);

        CapitalFlowOverviewResp actual = controller.refresh("all", 20).getData();

        assertSame(expected, actual);
        verify(userAuthService).requireAdmin();
        verify(capitalFlowService).refresh("all", 20);
    }
}
