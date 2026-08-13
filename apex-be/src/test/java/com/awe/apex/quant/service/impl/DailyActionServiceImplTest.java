package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.domain.entity.DailyAction;
import com.awe.apex.quant.mapper.DailyActionMapper;
import com.awe.apex.quant.service.IPaperService;
import com.awe.apex.quant.service.ISignalService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DailyActionServiceImplTest {

    @Test
    void reusesPublishedDecisionInsteadOfOverwritingIt() {
        DailyActionMapper dailyActionMapper = mock(DailyActionMapper.class);
        ISignalService signalService = mock(ISignalService.class);
        IPaperService paperService = mock(IPaperService.class);
        DailyActionServiceImpl service = new DailyActionServiceImpl();
        ReflectionTestUtils.setField(service, "dailyActionMapper", dailyActionMapper);
        ReflectionTestUtils.setField(service, "signalService", signalService);
        ReflectionTestUtils.setField(service, "paperService", paperService);
        DailyAction reduction = DailyAction.builder()
                .id(1L)
                .runId(9L)
                .actionDate(LocalDate.of(2026, 8, 7))
                .code("000001")
                .action("REDUCE")
                .build();
        when(dailyActionMapper.selectList(any())).thenReturn(List.of(reduction));

        List<DailyAction> actions = service.run(LocalDate.of(2026, 8, 7));

        assertEquals(List.of(reduction), actions);
        verify(dailyActionMapper, never()).delete(any());
        verifyNoInteractions(signalService, paperService);
    }
}
