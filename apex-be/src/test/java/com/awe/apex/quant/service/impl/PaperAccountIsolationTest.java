package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.entity.ApexUserProfile;
import com.awe.apex.quant.domain.entity.PaperAccount;
import com.awe.apex.quant.domain.entity.PaperOrder;
import com.awe.apex.quant.mapper.ApexUserProfileMapper;
import com.awe.apex.quant.mapper.PaperAccountMapper;
import com.awe.apex.quant.mapper.PaperOrderMapper;
import com.awe.apex.quant.mapper.PaperPositionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaperAccountIsolationTest {

    private final PaperAccountMapper paperAccountMapper = mock(PaperAccountMapper.class);
    private final PaperPositionMapper paperPositionMapper = mock(PaperPositionMapper.class);
    private final PaperOrderMapper paperOrderMapper = mock(PaperOrderMapper.class);
    private final ApexUserProfileMapper userProfileMapper = mock(ApexUserProfileMapper.class);
    private final ApexUserContext userContext = mock(ApexUserContext.class);
    private final PaperServiceImpl service = new PaperServiceImpl();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "paperAccountMapper", paperAccountMapper);
        ReflectionTestUtils.setField(service, "paperPositionMapper", paperPositionMapper);
        ReflectionTestUtils.setField(service, "paperOrderMapper", paperOrderMapper);
        ReflectionTestUtils.setField(service, "apexUserProfileMapper", userProfileMapper);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        when(userContext.currentUserIdOrNull()).thenReturn(7L);
    }

    @Test
    void rejectsReadingForeignAccountAndPositions() {
        when(paperAccountMapper.selectById(22L)).thenReturn(PaperAccount.builder()
                .id(22L)
                .userId(8L)
                .build());

        assertThrows(BusinessException.class, () -> service.getAccount(22L));
        assertThrows(BusinessException.class, () -> service.listPositions(22L));

        verify(paperPositionMapper, never()).selectList(any());
    }

    @Test
    void rejectsDestructiveOperationOnForeignAccount() {
        when(paperAccountMapper.selectById(22L)).thenReturn(PaperAccount.builder()
                .id(22L)
                .userId(8L)
                .build());

        assertThrows(BusinessException.class, () -> service.closeAll(22L));

        verify(paperOrderMapper, never()).insert(any(PaperOrder.class));
    }

    @Test
    void backgroundUserResolvesItsOwnDefaultAccount() {
        ApexUserProfile profile = new ApexUserProfile();
        profile.setUserId(7L);
        profile.setPaperAccountId(11L);
        when(userProfileMapper.selectOne(any())).thenReturn(profile);
        when(paperAccountMapper.selectById(11L)).thenReturn(PaperAccount.builder()
                .id(11L)
                .userId(7L)
                .build());

        PaperAccount account = service.defaultAccount();

        assertEquals(11L, account.getId());
    }

    @Test
    void rejectsAccountAccessWithoutUserContext() {
        when(userContext.currentUserIdOrNull()).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getAccount(11L));

        assertEquals("未获取到当前用户", exception.getMessage());
        verifyNoInteractions(paperAccountMapper);
    }
}
