package com.awe.apex.common.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.awe.apex.common.constant.Constants;
import com.awe.apex.common.util.SpringUtils;
import com.awe.apex.manager.domain.user.entity.User;
import com.awe.apex.manager.service.IUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RequestLogFilterTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLogFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private ApplicationContext originalApplicationContext;
    private IUserService userService;
    private RequestLogFilter filter;

    @BeforeEach
    void setUp() {
        originalApplicationContext = SpringUtils.getApplicationContext();
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("objectMapper", new ObjectMapper());
        new SpringUtils().setApplicationContext(applicationContext);

        userService = mock(IUserService.class);
        filter = new RequestLogFilter();
        ReflectionTestUtils.setField(filter, "userService", userService);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        MDC.clear();
        new SpringUtils().setApplicationContext(originalApplicationContext);
    }

    @Test
    void logsSanitizedJsonAndKeepsBodyReadable() throws Exception {
        String body = "{\"phone\":\"13812345678\",\"password\":\"secret-123\","
                + "\"idCard\":\"500236198909270662\",\"query\":\"新能源\"}";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.addHeader(Constants.TRACE_ID, "trace_20260817");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<StpUtil> stpUtil = org.mockito.Mockito.mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdDefaultNull).thenReturn(null);
            filter.doFilterInternal(request, response, (wrappedRequest, wrappedResponse) -> {
                String receivedBody = new String(wrappedRequest.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                assertEquals(body, receivedBody);
            });
        }

        List<ILoggingEvent> events = appender.list;
        assertEquals(4, events.size());
        assertTrue(events.stream().allMatch(event -> "trace_20260817".equals(event.getMDCPropertyMap().get(Constants.TRACE_ID))));
        assertTrue(events.stream().allMatch(event -> "138****5678".equals(event.getMDCPropertyMap().get(Constants.LOG_USER))));
        assertEquals("trace_20260817", response.getHeader(Constants.TRACE_ID));
        String messages = messages(events);
        assertTrue(messages.contains("参数类型[json]"));
        assertTrue(messages.contains("新能源"));
        assertTrue(messages.contains("[已脱敏]"));
        assertFalse(messages.contains("13812345678"));
        assertFalse(messages.contains("secret-123"));
        assertFalse(messages.contains("500236198909270662"));
        assertNull(MDC.get(Constants.TRACE_ID));
        assertNull(MDC.get(Constants.LOG_USER));
    }

    @Test
    void usesSessionUserAndWarnsForClientError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/stocks/search");
        request.setParameter("keyword", "光伏");
        request.addHeader(Constants.TRACE_ID, "invalid trace id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        SaSession session = mock(SaSession.class);
        when(session.get(Constants.PHONE)).thenReturn("13987654321");
        when(session.get(Constants.NICK_NAME)).thenReturn("量化用户");

        try (MockedStatic<StpUtil> stpUtil = org.mockito.Mockito.mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdDefaultNull).thenReturn(7L);
            stpUtil.when(() -> StpUtil.getSessionByLoginId(7L, false)).thenReturn(session);
            filter.doFilterInternal(request, response, (wrappedRequest, wrappedResponse) -> response.setStatus(404));
        }

        List<ILoggingEvent> events = appender.list;
        String traceId = response.getHeader(Constants.TRACE_ID);
        assertTrue(traceId.matches("[a-f0-9]{16}"));
        assertTrue(events.stream().allMatch(event -> traceId.equals(event.getMDCPropertyMap().get(Constants.TRACE_ID))));
        assertTrue(events.stream().allMatch(event -> "量化用户(139****4321)".equals(
                event.getMDCPropertyMap().get(Constants.LOG_USER))));
        assertEquals(Level.INFO, events.get(0).getLevel());
        assertEquals(Level.INFO, events.get(1).getLevel());
        assertEquals(Level.WARN, events.get(2).getLevel());
        assertEquals(Level.WARN, events.get(3).getLevel());
        assertTrue(messages(events).contains("状态[404]"));
        verifyNoInteractions(userService);
    }

    @Test
    void resolvesUserFromBearerTokenBeforeAuthenticationInterceptor() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/decision/advice");
        request.addHeader("Authorization", "Bearer bearer-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        User user = User.builder().id(7L).nickName("Awe").phone("13812345678").build();
        when(userService.getById(7L)).thenReturn(user);

        try (MockedStatic<StpUtil> stpUtil = org.mockito.Mockito.mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdDefaultNull).thenThrow(new IllegalStateException("认证上下文尚未建立"));
            stpUtil.when(() -> StpUtil.getLoginIdByToken("bearer-token")).thenReturn(7L);
            filter.doFilterInternal(request, response, (wrappedRequest, wrappedResponse) -> {
            });
        }

        assertTrue(appender.list.stream().allMatch(event -> "Awe(138****5678)".equals(
                event.getMDCPropertyMap().get(Constants.LOG_USER))));
    }

    @Test
    void logsUnhandledExceptionAsErrorAndAlwaysClearsMdc() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/failure");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<StpUtil> stpUtil = org.mockito.Mockito.mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdDefaultNull).thenReturn(null);
            assertThrows(ServletException.class, () -> filter.doFilterInternal(request, response,
                    (wrappedRequest, wrappedResponse) -> {
                        throw new ServletException("boom");
                    }));
        }

        List<ILoggingEvent> events = appender.list;
        assertEquals(Level.ERROR, events.get(2).getLevel());
        assertEquals(Level.ERROR, events.get(3).getLevel());
        assertTrue(messages(events).contains("异常[ServletException]"));
        assertNull(MDC.get(Constants.TRACE_ID));
        assertNull(MDC.get(Constants.LOG_USER));
    }

    @Test
    void warnsForHandledBusinessFailureEvenWhenHttpStatusIsOk() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/portfolio");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<StpUtil> stpUtil = org.mockito.Mockito.mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdDefaultNull).thenReturn(null);
            filter.doFilterInternal(request, response, (wrappedRequest, wrappedResponse) ->
                    wrappedRequest.setAttribute(Constants.REQUEST_LOG_LEVEL, Constants.LOG_LEVEL_WARN));
        }

        assertEquals(200, response.getStatus());
        assertEquals(Level.WARN, appender.list.get(2).getLevel());
        assertEquals(Level.WARN, appender.list.get(3).getLevel());
    }

    @Test
    void skipsRequestLogsForApplicationHealthEndpointOnly() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/apex/api/health");
        request.setContextPath("/apex");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (wrappedRequest, wrappedResponse) -> chainCalled.set(true));

        assertTrue(chainCalled.get());
        assertTrue(appender.list.isEmpty());
        assertNull(response.getHeader(Constants.TRACE_ID));
        verifyNoInteractions(userService);

        MockHttpServletRequest localHealthRequest = new MockHttpServletRequest("GET", "/api/health");
        assertTrue(filter.shouldNotFilter(localHealthRequest));

        MockHttpServletRequest businessHealthRequest = new MockHttpServletRequest(
                "GET", "/apex/api/paper/health-score");
        businessHealthRequest.setContextPath("/apex");
        assertFalse(filter.shouldNotFilter(businessHealthRequest));
    }

    @Test
    void skipsRequestLogsForApplicationReadinessEndpoint() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/apex/api/health/ready");
        request.setContextPath("/apex");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (wrappedRequest, wrappedResponse) -> chainCalled.set(true));

        assertTrue(chainCalled.get());
        assertTrue(appender.list.isEmpty());
        assertNull(response.getHeader(Constants.TRACE_ID));
        verifyNoInteractions(userService);

        MockHttpServletRequest localReadinessRequest = new MockHttpServletRequest("GET", "/api/health/ready");
        assertTrue(filter.shouldNotFilter(localReadinessRequest));
    }

    private String messages(List<ILoggingEvent> events) {
        StringBuilder messages = new StringBuilder();
        for (ILoggingEvent event : events) {
            messages.append(event.getFormattedMessage()).append('\n');
        }
        return messages.toString();
    }
}
