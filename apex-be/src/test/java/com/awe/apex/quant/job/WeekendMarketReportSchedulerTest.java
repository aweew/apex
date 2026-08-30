package com.awe.apex.quant.job;

import com.awe.apex.quant.domain.dto.WeekendMarketReportResp;
import com.awe.apex.quant.service.IWeekendMarketReportService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 周末研报定时任务测试。
 */
class WeekendMarketReportSchedulerTest {

    @Test
    void shouldRefreshReportAtSundayNinePmShanghaiTime() throws Exception {
        IWeekendMarketReportService reportService = mock(IWeekendMarketReportService.class);
        when(reportService.refresh()).thenReturn(WeekendMarketReportResp.builder()
                .reportDate(LocalDate.now()).lastTradeDate(LocalDate.now().minusDays(2))
                .reportSource("RULE").dataLevel("GREEN").weekendNews(List.of()).marketOpinions(List.of()).build());
        WeekendMarketReportScheduler scheduler = new WeekendMarketReportScheduler();
        ReflectionTestUtils.setField(scheduler, "weekendMarketReportService", reportService);

        scheduler.generateWeekendMarketReport();

        verify(reportService).refresh();
        Method method = WeekendMarketReportScheduler.class.getMethod("generateWeekendMarketReport");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        assertEquals("0 0 21 * * SUN", scheduled.cron());
        assertEquals("Asia/Shanghai", scheduled.zone());
    }
}
