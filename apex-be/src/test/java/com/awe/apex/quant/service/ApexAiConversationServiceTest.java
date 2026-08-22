package com.awe.apex.quant.service;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.ApexAiAnalysisResp;
import com.awe.apex.quant.domain.dto.ApexAiAnalyzeReq;
import com.awe.apex.quant.domain.entity.ApexAiConversation;
import com.awe.apex.quant.domain.entity.ApexAiMessage;
import com.awe.apex.quant.mapper.ApexAiConversationMapper;
import com.awe.apex.quant.mapper.ApexAiMessageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApexAiConversationServiceTest {

    private final ApexAiConversationMapper conversationMapper = mock(ApexAiConversationMapper.class);
    private final ApexAiMessageMapper messageMapper = mock(ApexAiMessageMapper.class);
    private final ApexUserContext userContext = mock(ApexUserContext.class);
    private final ApexAiConversationService service = new ApexAiConversationService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "conversationMapper", conversationMapper);
        ReflectionTestUtils.setField(service, "messageMapper", messageMapper);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        when(userContext.currentUserId()).thenReturn(7L);
    }

    @Test
    void rejectsConversationOwnedByAnotherUser() {
        when(conversationMapper.selectOwnedConversation(18L, 7L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.openConversation(18L, "继续分析"));
    }

    @Test
    void createsConversationAndPersistsBothRoles() {
        when(conversationMapper.insert(any(ApexAiConversation.class))).thenAnswer(invocation -> {
            ApexAiConversation conversation = invocation.getArgument(0);
            conversation.setId(21L);
            return 1;
        });
        ApexAiAnalyzeReq request = ApexAiAnalyzeReq.builder()
                .question("为什么今天收益下跌？")
                .analysisType("PORTFOLIO")
                .portfolioId(8L)
                .build();
        ApexAiAnalysisResp analysis = ApexAiAnalysisResp.builder()
                .requestId("req-1")
                .analysisType("PORTFOLIO")
                .summary("半导体板块是主要拖累。")
                .aiEnhanced(false)
                .build();

        Long conversationId = service.openConversation(null, request.getQuestion());
        service.saveAnalysis(conversationId, request, analysis, 32L);

        assertEquals(21L, conversationId);
        verify(messageMapper).insertUserMessage(eq(21L), eq(7L), eq(request), eq("req-1"));
        verify(messageMapper).insertAssistantMessage(eq(21L), eq(7L), eq(analysis), eq(32L));
        verify(conversationMapper).touchConversation(21L, 7L, "PORTFOLIO", "半导体板块是主要拖累。", 2);
    }

    @Test
    void returnsRealRoleHistoryInChronologicalOrder() {
        when(messageMapper.selectRecentMessages(21L, 7L, 10)).thenReturn(List.of(
                ApexAiMessage.builder().role("USER").content("先看组合").build(),
                ApexAiMessage.builder().role("ASSISTANT").content("组合今日下跌").build()));

        assertEquals(List.of("user", "assistant"), service.history(21L, 10).stream()
                .map(message -> message.getRole())
                .toList());
    }
}
