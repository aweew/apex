package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.BacktestExperimentDetailResp;
import com.awe.apex.quant.domain.dto.BacktestExperimentListResp;
import com.awe.apex.quant.domain.dto.RollingBacktestReq;
import com.awe.apex.quant.domain.dto.RollingBacktestResp;
import com.awe.apex.quant.service.IBacktestExperimentService;
import com.awe.apex.quant.service.IBacktestService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BacktestControllerTest {

    @Test
    void shouldExposeRollingEvaluationEndpoint() {
        IBacktestService backtestService = mock(IBacktestService.class);
        BacktestController controller = new BacktestController();
        ReflectionTestUtils.setField(controller, "backtestService", backtestService);
        RollingBacktestReq req = new RollingBacktestReq();
        RollingBacktestResp expected = RollingBacktestResp.builder().foldCount(3).build();
        when(backtestService.rollingEvaluate(req)).thenReturn(expected);

        Result<RollingBacktestResp> result = controller.rollingEvaluate(req);

        assertSame(expected, result.getData());
    }

    @Test
    void shouldExposeOwnedExperimentHistoryEndpoints() {
        IBacktestExperimentService experimentService = mock(IBacktestExperimentService.class);
        BacktestController controller = new BacktestController();
        ReflectionTestUtils.setField(controller, "backtestExperimentService", experimentService);
        BacktestExperimentListResp summary = BacktestExperimentListResp.builder().id(8L).build();
        BacktestExperimentDetailResp detail = BacktestExperimentDetailResp.builder().id(8L).build();
        when(experimentService.list(20)).thenReturn(List.of(summary));
        when(experimentService.detail(8L)).thenReturn(detail);

        Result<List<BacktestExperimentListResp>> listResult = controller.experiments(20);
        Result<BacktestExperimentDetailResp> detailResult = controller.experimentDetail(8L);
        Result<Void> removeResult = controller.removeExperiment(8L);

        assertEquals(8L, listResult.getData().get(0).getId());
        assertSame(detail, detailResult.getData());
        assertEquals(0, removeResult.getCode());
        verify(experimentService).remove(8L);
    }
}
