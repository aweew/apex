package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.domain.dto.SectorConstituentResp;
import com.awe.apex.quant.domain.entity.SectorBasic;
import com.awe.apex.quant.domain.entity.SectorConstituent;
import com.awe.apex.quant.mapper.SectorBasicMapper;
import com.awe.apex.quant.mapper.SectorConstituentMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
}
