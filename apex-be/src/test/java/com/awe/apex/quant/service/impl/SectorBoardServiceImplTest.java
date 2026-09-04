package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.domain.dto.SectorBoardItem;
import com.awe.apex.quant.domain.dto.SectorConstituentResp;
import com.awe.apex.quant.domain.dto.SectorRotationResp;
import com.awe.apex.quant.domain.entity.SectorBasic;
import com.awe.apex.quant.domain.entity.SectorConstituent;
import com.awe.apex.quant.domain.entity.SectorQuote;
import com.awe.apex.quant.mapper.SectorBasicMapper;
import com.awe.apex.quant.mapper.SectorConstituentMapper;
import com.awe.apex.quant.mapper.SectorQuoteMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SectorBoardServiceImplTest {

    @Test
    void shouldUseLatestAvailableSnapshotWhenRequestedDateHasNotBeenSynced() {
        SectorBoardServiceImpl service = new SectorBoardServiceImpl();
        LocalDate actualTradeDate = LocalDate.of(2026, 8, 10);
        LocalDate resolvedDate = ReflectionTestUtils.invokeMethod(
                service,
                "resolveTradeDate",
                "2026-08-11",
                List.of(actualTradeDate, LocalDate.of(2026, 8, 7)),
                actualTradeDate
        );

        assertEquals(actualTradeDate, resolvedDate);
    }

    @Test
    void shouldUseLatestConstituentSnapshotNotAfterRequestedDate() {
        SectorBoardServiceImpl service = new SectorBoardServiceImpl();
        SectorConstituentMapper constituentMapper = mock(SectorConstituentMapper.class);
        SectorBasicMapper basicMapper = mock(SectorBasicMapper.class);
        LocalDate cachedTradeDate = LocalDate.of(2026, 8, 14);
        SectorConstituent cachedRow = SectorConstituent.builder()
                .sectorCode("BK1510")
                .boardType("INDUSTRY")
                .stockCode("300313")
                .stockName("天山生物")
                .latestPrice(new BigDecimal("10.73"))
                .pctChg(new BigDecimal("20.02"))
                .tradeDate(cachedTradeDate)
                .build();

        when(constituentMapper.selectOne(any())).thenReturn(cachedRow);
        when(constituentMapper.selectList(any())).thenReturn(List.of(cachedRow));
        when(basicMapper.selectOne(any())).thenReturn(SectorBasic.builder()
                .code("BK1510")
                .name("其他养殖")
                .boardType("INDUSTRY")
                .build());
        ReflectionTestUtils.setField(service, "sectorConstituentMapper", constituentMapper);
        ReflectionTestUtils.setField(service, "sectorBasicMapper", basicMapper);

        SectorConstituentResp response = service.constituents(
                "BK1510", "INDUSTRY", "pctChg", "desc", "2026-08-17");

        assertEquals(cachedTradeDate, response.getTradeDate());
        assertEquals(1, response.getItems().size());
        assertEquals("300313", response.getItems().get(0).getCode());
    }

    @Test
    void shouldQueryFiveDistinctTradingDaysForRotation() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SectorQuote.class);
        SectorBoardServiceImpl service = new SectorBoardServiceImpl();
        SectorQuoteMapper sectorQuoteMapper = mock(SectorQuoteMapper.class);
        List<SectorQuote> dateRows = List.of(
                sectorQuote(LocalDate.of(2026, 8, 31), "视频媒体", "20.00"),
                sectorQuote(LocalDate.of(2026, 8, 28), "氮肥", "7.33"),
                sectorQuote(LocalDate.of(2026, 8, 27), "教育运营", "7.64"),
                sectorQuote(LocalDate.of(2026, 8, 26), "电子化学品", "6.52"),
                sectorQuote(LocalDate.of(2026, 8, 25), "半导体设备", "6.18")
        );
        when(sectorQuoteMapper.selectList(any())).thenReturn(dateRows, dateRows);
        ReflectionTestUtils.setField(service, "sectorQuoteMapper", sectorQuoteMapper);

        SectorRotationResp response = service.rotation("INDUSTRY", null, 5);

        assertEquals(5, response.getDays().size());
        assertEquals("INDUSTRY 轮动 · 近 5 日 Top5", response.getMessage());
        ArgumentCaptor<Wrapper<SectorQuote>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(sectorQuoteMapper, times(2)).selectList(queryCaptor.capture());
        assertTrue(queryCaptor.getAllValues().get(0).getSqlSegment().contains("GROUP BY trade_date"));
    }

    @Test
    void shouldRankMultiDayStrengthAndCapitalAboveLatestSessionSpike() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SectorQuote.class);
        SectorBoardServiceImpl service = new SectorBoardServiceImpl();
        SectorQuoteMapper sectorQuoteMapper = mock(SectorQuoteMapper.class);
        LocalDate tradeDate = LocalDate.of(2026, 8, 18);
        SectorQuote latestDate = SectorQuote.builder().tradeDate(tradeDate).build();
        SectorQuote latestSessionSpike = SectorQuote.builder()
                .code("BK1001").name("单日脉冲").boardType("CONCEPT").tradeDate(tradeDate)
                .pctChg(new BigDecimal("10.00")).pctChg3d(new BigDecimal("0.20"))
                .pctChg5d(new BigDecimal("0.30")).netInflow(new BigDecimal("1"))
                .amount(new BigDecimal("100")).limitUpCount(10).maxLianban(5).build();
        SectorQuote multiDayStrength = SectorQuote.builder()
                .code("BK1002").name("多周期修复").boardType("CONCEPT").tradeDate(tradeDate)
                .pctChg(new BigDecimal("-0.20")).pctChg3d(new BigDecimal("2.00"))
                .pctChg5d(new BigDecimal("-1.00")).netInflow(new BigDecimal("10"))
                .amount(new BigDecimal("100")).limitUpCount(1).maxLianban(1).build();
        when(sectorQuoteMapper.selectList(any())).thenReturn(
                List.of(latestDate), List.of(latestSessionSpike, multiDayStrength));
        ReflectionTestUtils.setField(service, "sectorQuoteMapper", sectorQuoteMapper);

        List<SectorBoardItem> result = service.mainline(null, 2);

        assertEquals("多周期修复", result.get(0).getName());
    }

    private SectorQuote sectorQuote(LocalDate tradeDate, String name, String pctChg) {
        return SectorQuote.builder()
                .code(name)
                .name(name)
                .boardType("INDUSTRY")
                .tradeDate(tradeDate)
                .pctChg(new BigDecimal(pctChg))
                .build();
    }
}
