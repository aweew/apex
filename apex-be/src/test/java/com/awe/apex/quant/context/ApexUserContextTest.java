package com.awe.apex.quant.context;

import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

class ApexUserContextTest {

    private final ApexUserContext userContext = new ApexUserContext();

    @Test
    void usesRequestUserWhenNoBackgroundUserIsBound() {
        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(11L);

            assertEquals(11L, userContext.currentUserId());
        }
    }

    @Test
    void backgroundUserTakesPrecedenceAndRestoresOuterContext() {
        AtomicLong observedUserId = new AtomicLong();
        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(11L);

            userContext.runAsUser(22L, () -> observedUserId.set(userContext.currentUserId()));

            assertEquals(22L, observedUserId.get());
            assertEquals(11L, userContext.currentUserId());
        }
    }

    @Test
    void clearsBackgroundUserAfterFailure() {
        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(11L);

            assertThrows(IllegalStateException.class,
                    () -> userContext.runAsUser(22L, () -> {
                        throw new IllegalStateException("boom");
                    }));

            assertEquals(11L, userContext.currentUserId());
        }
    }

    @Test
    void returnsSupplierResultAndRestoresOuterContext() {
        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(11L);

            Long result = userContext.runAsUser(22L, (Supplier<Long>) userContext::currentUserId);

            assertEquals(22L, result);
            assertEquals(11L, userContext.currentUserId());
        }
    }

    @Test
    void clearsSupplierBackgroundUserAfterFailure() {
        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(11L);

            assertThrows(IllegalStateException.class,
                    () -> userContext.runAsUser(22L, () -> {
                        throw new IllegalStateException("boom");
                    }));

            assertEquals(11L, userContext.currentUserId());
        }
    }
}
