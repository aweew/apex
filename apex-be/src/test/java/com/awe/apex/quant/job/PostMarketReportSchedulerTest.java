package com.awe.apex.quant.job;

import com.awe.apex.quant.domain.dto.PostMarketReportResp;
import com.awe.apex.quant.service.IPostMarketReportService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 盘后总结调度测试。
 */
class PostMarketReportSchedulerTest {

    @Test
    void shouldGenerateAtEighteenThirtyOnWeekdays() throws Exception {
        Method scheduledMethod = PostMarketReportScheduler.class.getMethod("generatePostMarketReport");
        Scheduled scheduled = scheduledMethod.getAnnotation(Scheduled.class);

        assertEquals("0 30 18 * * MON-FRI", scheduled.cron());
        assertEquals("Asia/Shanghai", scheduled.zone());
    }

    @Test
    void shouldGenerateLatestReport() {
        IPostMarketReportService reportService = mock(IPostMarketReportService.class);
        when(reportService.refresh()).thenReturn(PostMarketReportResp.builder()
                .tradeDate(LocalDate.of(2026, 8, 31))
                .dataLevel("GREEN")
                .reportSource("RULE")
                .build());
        PostMarketReportScheduler scheduler = new PostMarketReportScheduler();
        ReflectionTestUtils.setField(scheduler, "postMarketReportService", reportService);

        scheduler.generatePostMarketReport(LocalDate.of(2026, 8, 31));

        verify(reportService).refresh();
    }

    @Test
    void shouldSkipWeekdayHoliday() {
        IPostMarketReportService reportService = mock(IPostMarketReportService.class);
        PostMarketReportScheduler scheduler = new PostMarketReportScheduler();
        ReflectionTestUtils.setField(scheduler, "postMarketReportService", reportService);

        scheduler.generatePostMarketReport(LocalDate.of(2026, 10, 2));

        verify(reportService, never()).refresh();
    }
}
