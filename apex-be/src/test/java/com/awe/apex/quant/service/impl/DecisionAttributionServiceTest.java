package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.domain.dto.DecisionAttributionResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.DailyAction;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.DailyActionMapper;
import com.awe.apex.quant.mapper.MarketBriefingSnapshotMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DecisionAttributionServiceTest {

    private static final LocalDate ACTION_DATE = LocalDate.of(2026, 8, 12);

    private final DecisionServiceImpl service = new DecisionServiceImpl();
    private final DailyActionMapper dailyActionMapper = mock(DailyActionMapper.class);
    private final BarDailyMapper barDailyMapper = mock(BarDailyMapper.class);
    private final MarketBriefingSnapshotMapper marketBriefingSnapshotMapper =
            mock(MarketBriefingSnapshotMapper.class);

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DailyAction.class);
        TableInfoHelper.initTableInfo(assistant, BarDaily.class);
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "dailyActionMapper", dailyActionMapper);
        ReflectionTestUtils.setField(service, "barDailyMapper", barDailyMapper);
        ReflectionTestUtils.setField(service, "marketBriefingSnapshotMapper", marketBriefingSnapshotMapper);
    }

    @Test
    void includesReductionActionsInSellAttribution() {
        DailyAction dateRow = DailyAction.builder().actionDate(ACTION_DATE).build();
        DailyAction reduction = DailyAction.builder()
                .actionDate(ACTION_DATE)
                .code("600000")
                .action("REDUCE")
                .strategyId("PORTFOLIO_DRAWDOWN")
                .build();
        when(dailyActionMapper.selectList(any())).thenAnswer(invocation -> {
            AbstractWrapper<?, ?, ?> wrapper = invocation.getArgument(0);
            wrapper.getSqlSegment();
            if (wrapper.getParamNameValuePairs().isEmpty()) {
                return List.of(dateRow);
            }
            if (wrapper.getParamNameValuePairs().containsValue("REDUCE")) {
                return List.of(reduction);
            }
            return List.of();
        });
        when(barDailyMapper.selectOne(any())).thenReturn(BarDaily.builder()
                .code("600000")
                .tradeDate(LocalDate.of(2026, 8, 13))
                .pctChg(new BigDecimal("-1.25"))
                .build());

        DecisionAttributionResp attribution = service.attribution(20);

        assertEquals(1, attribution.getBySellStrategy().size());
        assertEquals("PORTFOLIO_DRAWDOWN", attribution.getBySellStrategy().get(0).getKey());
        assertEquals(1, attribution.getBySellStrategy().get(0).getSampleCount());
        assertEquals("近 1 个决策日 · 买 0 / 卖 1 · 按次日涨跌归因（缺日线样本不计入均值）",
                attribution.getMessage());
    }
}
