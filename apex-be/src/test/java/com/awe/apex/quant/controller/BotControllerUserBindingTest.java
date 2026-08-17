package com.awe.apex.quant.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.bot.config.ApexBotProperties;
import com.awe.apex.quant.bot.service.IBotQuestionService;
import com.awe.apex.quant.bot.service.IBotToolService;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.BotAskReq;
import com.awe.apex.quant.domain.dto.BotAskResp;
import com.awe.apex.quant.domain.dto.BotToolReq;
import com.awe.apex.quant.domain.dto.BotToolResp;
import com.awe.apex.quant.service.ApexUserAuthService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotControllerUserBindingTest {

    @Test
    void executesAskAndToolAsTrustedConfiguredUser() {
        ApexUserContext userContext = new ApexUserContext();
        ApexBotProperties properties = new ApexBotProperties();
        properties.setApexUserId(7L);
        properties.setExternalUserId("trusted-user");
        IBotQuestionService questionService = mock(IBotQuestionService.class);
        IBotToolService toolService = mock(IBotToolService.class);
        ApexUserAuthService userAuthService = mock(ApexUserAuthService.class);
        List<Long> observedUserIds = new ArrayList<>();
        when(questionService.ask(any())).thenAnswer(invocation -> {
            observedUserIds.add(userContext.currentUserId());
            return BotAskResp.builder().requestId("ask-1").intent("MARKET_BRIEFING").build();
        });
        when(toolService.execute(any())).thenAnswer(invocation -> {
            observedUserIds.add(userContext.currentUserId());
            return BotToolResp.builder().requestId("tool-1").intent("PORTFOLIO_STATUS").build();
        });
        BotController controller = buildController(
                questionService, toolService, userAuthService, userContext, properties);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(99L);

            controller.ask(BotAskReq.builder().question("大盘怎么样").userId("trusted-user").build());
            BotToolReq toolRequest = new BotToolReq();
            toolRequest.setOperation("PORTFOLIO_STATUS");
            toolRequest.setUserId("trusted-user");
            toolRequest.setConversationId("conversation");
            controller.tool(toolRequest);

            assertEquals(List.of(7L, 7L), observedUserIds);
            assertEquals(99L, userContext.currentUserId());
            verify(userAuthService, times(2)).requireEnabledUser(7L);
        }
    }

    @Test
    void rejectsPrivateBotOperationsWithoutTrustedUserBinding() {
        ApexBotProperties properties = new ApexBotProperties();
        IBotQuestionService questionService = mock(IBotQuestionService.class);
        IBotToolService toolService = mock(IBotToolService.class);
        BotController controller = buildController(
                questionService, toolService, mock(ApexUserAuthService.class),
                new ApexUserContext(), properties);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> controller.ask(BotAskReq.builder().question("我的持仓").userId("unknown-user").build()));

        assertEquals("Bot API 未绑定 Apex 用户", exception.getMessage());
        verify(questionService, never()).ask(any());
    }

    @Test
    void rejectsForgedOrUnknownExternalUser() {
        ApexBotProperties properties = new ApexBotProperties();
        properties.setApexUserId(7L);
        properties.setExternalUserId("trusted-user");
        IBotQuestionService questionService = mock(IBotQuestionService.class);
        BotController controller = buildController(
                questionService, mock(IBotToolService.class), mock(ApexUserAuthService.class),
                new ApexUserContext(), properties);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> controller.ask(BotAskReq.builder().question("我的持仓").userId("forged-user").build()));

        assertEquals("Bot 用户无权访问该 Apex 账户", exception.getMessage());
        verify(questionService, never()).ask(any());
    }

    @Test
    void rejectsAskAndToolWhenBoundApexUserIsDisabled() {
        ApexBotProperties properties = new ApexBotProperties();
        properties.setApexUserId(7L);
        properties.setExternalUserId("trusted-user");
        IBotQuestionService questionService = mock(IBotQuestionService.class);
        IBotToolService toolService = mock(IBotToolService.class);
        ApexUserAuthService userAuthService = mock(ApexUserAuthService.class);
        doThrow(new BusinessException("账户不存在或已禁用"))
                .when(userAuthService).requireEnabledUser(7L);
        BotController controller = buildController(
                questionService, toolService, userAuthService, new ApexUserContext(), properties);
        BotAskReq askRequest = BotAskReq.builder()
                .question("我的持仓")
                .userId("trusted-user")
                .build();
        BotToolReq toolRequest = new BotToolReq();
        toolRequest.setOperation("PORTFOLIO_STATUS");
        toolRequest.setUserId("trusted-user");
        toolRequest.setConversationId("conversation");

        assertThrows(BusinessException.class, () -> controller.ask(askRequest));
        assertThrows(BusinessException.class, () -> controller.tool(toolRequest));

        verify(questionService, never()).ask(any());
        verify(toolService, never()).execute(any());
    }

    private BotController buildController(IBotQuestionService questionService,
                                          IBotToolService toolService,
                                          ApexUserAuthService userAuthService,
                                          ApexUserContext userContext,
                                          ApexBotProperties properties) {
        BotController controller = new BotController();
        ReflectionTestUtils.setField(controller, "botQuestionService", questionService);
        ReflectionTestUtils.setField(controller, "botToolService", toolService);
        ReflectionTestUtils.setField(controller, "userAuthService", userAuthService);
        ReflectionTestUtils.setField(controller, "userContext", userContext);
        ReflectionTestUtils.setField(controller, "botProperties", properties);
        return controller;
    }
}
