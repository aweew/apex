package com.awe.apex.quant.bot.auth;

import com.awe.apex.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class BotHmacAuthFilterTest {

    private BotHmacAuthService authService;
    private BotHmacAuthFilter filter;

    @BeforeEach
    void setUp() {
        authService = mock(BotHmacAuthService.class);
        filter = new BotHmacAuthFilter();
        ReflectionTestUtils.setField(filter, "authService", authService);
        ReflectionTestUtils.setField(filter, "objectMapper", new ObjectMapper());
    }

    @Test
    void authenticatesCanonicalServletPathAndPreservesBody() throws Exception {
        byte[] body = "{\"question\":\"今天大盘怎么样\"}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = request(body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        verify(authService).validate(eq("POST"), eq("/apex/bot/v1/ask"), eq("key"), eq("1"),
                eq("nonce"), eq("digest"), eq("signature"), any(byte[].class));
        verify(filterChain).doFilter(any(CachedBodyHttpServletRequest.class), eq(response));
    }

    @Test
    void returnsUnauthorizedWhenAuthenticationFails() throws Exception {
        MockHttpServletRequest request = request("{}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        doThrow(new BusinessException("签名无效")).when(authService).validate(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any());

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertEquals(true, response.getContentAsString().contains("签名无效"));
    }

    @Test
    void rejectsOversizedBodyBeforeAuthentication() throws Exception {
        MockHttpServletRequest request = request(new byte[8193]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(413, response.getStatus());
        verifyNoInteractions(authService, filterChain);
    }

    private MockHttpServletRequest request(byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/apex/bot/v1/ask");
        request.setContextPath("/apex");
        request.setServletPath("/bot/v1/ask");
        request.setContent(body);
        request.addHeader("X-Apex-Key", "key");
        request.addHeader("X-Apex-Timestamp", "1");
        request.addHeader("X-Apex-Nonce", "nonce");
        request.addHeader("X-Apex-Content-Sha256", "digest");
        request.addHeader("X-Apex-Signature", "signature");
        return request;
    }
}
