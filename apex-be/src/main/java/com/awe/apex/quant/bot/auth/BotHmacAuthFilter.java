package com.awe.apex.quant.bot.auth;

import com.awe.apex.common.api.Result;
import com.awe.apex.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Bot API HMAC 鉴权过滤器。
 */
@Component
public class BotHmacAuthFilter extends OncePerRequestFilter {

    private static final int MAX_BODY_BYTES = 8192;

    @Resource
    private BotHmacAuthService authService;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 校验签名后继续处理 Bot 请求。
     *
     * @param request     HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            if (request.getContentLengthLong() > MAX_BODY_BYTES) {
                writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                        "Bot API 请求体不能超过 " + MAX_BODY_BYTES + " 字节");
                return;
            }
            CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request, MAX_BODY_BYTES);
            authService.validate(
                    request.getMethod(),
                    request.getContextPath() + request.getServletPath(),
                    request.getHeader("X-Apex-Key"),
                    request.getHeader("X-Apex-Timestamp"),
                    request.getHeader("X-Apex-Nonce"),
                    request.getHeader("X-Apex-Content-Sha256"),
                    request.getHeader("X-Apex-Signature"),
                    cachedRequest.getCachedBody());
            filterChain.doFilter(cachedRequest, response);
        } catch (BotRequestBodyTooLargeException ex) {
            writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, ex.getMessage());
        } catch (BusinessException ex) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, ex.getMessage());
        }
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Result.failure(status, message)));
    }

    /**
     * 仅过滤 Bot API，预检请求由 CORS 处理。
     *
     * @param request HTTP 请求
     * @return true=跳过
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || !request.getRequestURI().startsWith(request.getContextPath() + "/bot/");
    }
}
