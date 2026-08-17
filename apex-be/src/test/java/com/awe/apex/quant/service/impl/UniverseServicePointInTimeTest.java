package com.awe.apex.quant.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.domain.dto.UniverseRefreshReq;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.UniverseRefreshResp;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.UniverseSnapshot;
import com.awe.apex.quant.domain.entity.Watchlist;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.UniverseSnapshotMapper;
import com.awe.apex.quant.mapper.WatchlistMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UniverseServicePointInTimeTest {

    private static final Long CURRENT_USER_ID = 7L;

    private final WatchlistMapper watchlistMapper = mock(WatchlistMapper.class);
    private final BarDailyMapper barDailyMapper = mock(BarDailyMapper.class);
    private final UniverseSnapshotMapper universeSnapshotMapper = mock(UniverseSnapshotMapper.class);
    private final StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
    private final UniverseServiceImpl service = new UniverseServiceImpl();

    private MockedStatic<StpUtil> stpUtil;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, UniverseSnapshot.class);
        TableInfoHelper.initTableInfo(assistant, Watchlist.class);
        TableInfoHelper.initTableInfo(assistant, StockBasic.class);
    }

    @BeforeEach
    void setUp() {
        stpUtil = mockStatic(StpUtil.class);
        stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(CURRENT_USER_ID);
        ReflectionTestUtils.setField(service, "userContext", new ApexUserContext());
        ReflectionTestUtils.setField(service, "watchlistMapper", watchlistMapper);
        ReflectionTestUtils.setField(service, "barDailyMapper", barDailyMapper);
        ReflectionTestUtils.setField(service, "universeSnapshotMapper", universeSnapshotMapper);
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockBasicMapper);
    }

    @AfterEach
    void tearDown() {
        stpUtil.close();
    }

    @Test
    void shouldLoadLatestSnapshotAvailableAtBacktestBeginDate() {
        LocalDate backtestBeginDate = LocalDate.of(2024, 12, 31);
        UniverseSnapshot latestAvailable = UniverseSnapshot.builder().batchNo("20241231").build();
        when(universeSnapshotMapper.selectOne(any())).thenReturn(latestAvailable);
        when(universeSnapshotMapper.selectList(any())).thenReturn(List.of(latestAvailable));

        List<UniverseSnapshot> result = service.latestAsOf(backtestBeginDate);

        assertEquals(List.of(latestAvailable), result);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<UniverseSnapshot>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(universeSnapshotMapper).selectOne(queryCaptor.capture());
        LambdaQueryWrapper<UniverseSnapshot> query = (LambdaQueryWrapper<UniverseSnapshot>) queryCaptor.getValue();
        assertTrue(query.getSqlSegment().contains("as_of_date"));
        assertTrue(query.getSqlSegment().contains("user_id"));
        assertTrue(query.getParamNameValuePairs().containsValue(backtestBeginDate));
        assertTrue(query.getParamNameValuePairs().containsValue(CURRENT_USER_ID));
    }

    @Test
    void latestOrdersByBusinessDateBeforeInsertOrder() {
        when(universeSnapshotMapper.selectOne(any())).thenReturn(null);

        service.latest();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<UniverseSnapshot>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(universeSnapshotMapper).selectOne(queryCaptor.capture());
        LambdaQueryWrapper<UniverseSnapshot> query = (LambdaQueryWrapper<UniverseSnapshot>) queryCaptor.getValue();
        String sql = query.getSqlSegment();
        int asOfOrder = sql.indexOf("as_of_date");
        int idOrder = sql.lastIndexOf("id");
        assertTrue(asOfOrder >= 0);
        assertTrue(idOrder > asOfOrder);
    }

    @Test
    void shouldLoadRequestedBatchInsteadOfGlobalLatestBatch() {
        UniverseSnapshot expected = UniverseSnapshot.builder().batchNo("requested-batch").build();
        when(universeSnapshotMapper.selectList(any())).thenReturn(List.of(expected));

        List<UniverseSnapshot> result = service.listByBatchNo("requested-batch");

        assertEquals(List.of(expected), result);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<UniverseSnapshot>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(universeSnapshotMapper).selectList(queryCaptor.capture());
        LambdaQueryWrapper<UniverseSnapshot> query = (LambdaQueryWrapper<UniverseSnapshot>) queryCaptor.getValue();
        assertTrue(query.getSqlSegment().contains("batch_no"));
        assertTrue(query.getSqlSegment().contains("user_id"));
        assertTrue(query.getParamNameValuePairs().containsValue("requested-batch"));
        assertTrue(query.getParamNameValuePairs().containsValue(CURRENT_USER_ID));
    }

    @Test
    void shouldPersistRequestedAsOfDateWhenRefreshingHistoricalUniverse() {
        LocalDate asOfDate = LocalDate.now().minusDays(1);
        UniverseRefreshReq request = new UniverseRefreshReq();
        request.setScope("MARKET");
        request.setAsOfDate(asOfDate);
        when(stockBasicMapper.selectList(any())).thenReturn(List.of());
        when(barDailyMapper.selectMaps(any())).thenReturn(List.of(Map.of("code", "600519", "cnt", 100L)));

        service.refresh(request);

        ArgumentCaptor<UniverseSnapshot> snapshotCaptor = ArgumentCaptor.forClass(UniverseSnapshot.class);
        verify(universeSnapshotMapper).insert(snapshotCaptor.capture());
        assertEquals(asOfDate, ReflectionTestUtils.getField(snapshotCaptor.getValue(), "asOfDate"));
        assertEquals(CURRENT_USER_ID, snapshotCaptor.getValue().getUserId());
        assertTrue(snapshotCaptor.getValue().getReasonTags().contains("RECONSTRUCTED_AS_OF"));
    }

    @Test
    void shouldRejectFutureSnapshotDate() {
        UniverseRefreshReq request = new UniverseRefreshReq();
        request.setScope("MARKET");
        request.setAsOfDate(LocalDate.now().plusDays(1));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.refresh(request));

        assertEquals("股票池截止日期不能晚于今天", exception.getMessage());
        verify(universeSnapshotMapper, never()).insert(any(UniverseSnapshot.class));
    }

    @Test
    void shouldRejectCurrentWatchlistWhenRefreshingHistoricalUniverse() {
        UniverseRefreshReq request = new UniverseRefreshReq();
        request.setAsOfDate(LocalDate.now().minusDays(1));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.refresh(request));

        assertEquals("历史股票池只能基于截止日行情生成，请将 scope 设为 MARKET", exception.getMessage());
        verify(watchlistMapper, never()).selectList(any());
        verify(universeSnapshotMapper, never()).insert(any(UniverseSnapshot.class));
    }

    @Test
    void shouldRejectManualCodesWhenRefreshingHistoricalUniverse() {
        UniverseRefreshReq request = new UniverseRefreshReq();
        request.setCodes(List.of("600519"));
        request.setAsOfDate(LocalDate.now().minusDays(1));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.refresh(request));

        assertEquals("历史股票池只能基于截止日行情生成，请将 scope 设为 MARKET", exception.getMessage());
        verify(barDailyMapper, never()).selectMaps(any());
        verify(universeSnapshotMapper, never()).insert(any(UniverseSnapshot.class));
    }

    @Test
    void shouldLoadDefaultCandidatesFromCurrentUsersWatchlist() {
        when(watchlistMapper.selectList(any())).thenReturn(List.of());

        assertThrows(BusinessException.class, () -> service.refresh(new UniverseRefreshReq()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Watchlist>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(watchlistMapper).selectList(queryCaptor.capture());
        LambdaQueryWrapper<Watchlist> query = (LambdaQueryWrapper<Watchlist>) queryCaptor.getValue();
        assertTrue(query.getSqlSegment().contains("user_id"));
        assertTrue(query.getParamNameValuePairs().containsValue(CURRENT_USER_ID));
    }

    @Test
    void shouldGenerateCollisionResistantBatchNumberForEveryRefresh() {
        UniverseRefreshReq request = new UniverseRefreshReq();
        request.setCodes(List.of("600519"));
        when(stockBasicMapper.selectList(any())).thenReturn(List.of());
        when(barDailyMapper.selectMaps(any())).thenReturn(List.of(Map.of("code", "600519", "cnt", 100L)));

        UniverseRefreshResp first = service.refresh(request);
        UniverseRefreshResp second = service.refresh(request);

        assertEquals(32, first.getBatchNo().length());
        assertEquals(32, second.getBatchNo().length());
        assertNotEquals(first.getBatchNo(), second.getBatchNo());
    }
}
