package com.awe.apex.common.filter;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.awe.apex.common.constant.Constants;
import com.awe.apex.common.util.JsonUtils;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.manager.domain.user.entity.User;
import com.awe.apex.manager.service.IUserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 全局 HTTP 请求日志过滤器。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
public class RequestLogFilter extends OncePerRequestFilter {

    private static final int MAX_BODY_BYTES = 65_536;
    private static final int MAX_PARAMETER_TEXT_LENGTH = 8_192;
    private static final String HEALTH_ENDPOINT = "/api/health";
    private static final String MASKED_VALUE = "[已脱敏]";
    private static final String REQUEST_START = "====================[请求开始]====================";
    private static final String REQUEST_END = "====================[请求结束]====================";
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    @Resource
    private IUserService userService;

    /**
     * 健康检查由基础设施高频调用，不记录请求链路日志。
     *
     * @param request HTTP 请求
     * @return 是否跳过请求日志过滤器
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestPath = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.isNotBlank(contextPath) && requestPath.startsWith(contextPath)) {
            requestPath = requestPath.substring(contextPath.length());
        }
        return HEALTH_ENDPOINT.equals(requestPath);
    }

    /**
     * 记录请求开始与结束，并保证请求体可由后续 Controller 正常读取。
     *
     * @param request     HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        HttpServletRequest requestForChain = wrapJsonRequest(request);
        String traceId = resolveTraceId(request);
        String requestUser = resolveRequestUser(requestForChain);
        MDC.put(Constants.TRACE_ID, traceId);
        MDC.put(Constants.LOG_USER, StringUtils.isBlank(requestUser) ? "-" : requestUser);
        response.setHeader(Constants.TRACE_ID, traceId);

        Throwable requestFailure = null;
        logRequestStart(requestForChain);
        try {
            filterChain.doFilter(requestForChain, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            requestFailure = exception;
            throw exception;
        } catch (Error error) {
            requestFailure = error;
            throw error;
        } finally {
            logRequestEnd(requestForChain, response, startedAt, requestFailure);
            MDC.remove(Constants.TRACE_ID);
            MDC.remove(Constants.LOG_USER);
        }
    }

    private HttpServletRequest wrapJsonRequest(HttpServletRequest request) throws IOException {
        if (!isJsonRequest(request) || request instanceof RepeatedlyRequestWrapper) {
            return request;
        }
        long contentLength = request.getContentLengthLong();
        if (contentLength < 0 || contentLength > MAX_BODY_BYTES) {
            return request;
        }
        return new RepeatedlyRequestWrapper(request);
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(Constants.TRACE_ID);
        if (StringUtils.isNotBlank(traceId)) {
            traceId = traceId.trim();
        }
        if (StringUtils.isBlank(traceId) || !TRACE_ID_PATTERN.matcher(traceId).matches()) {
            return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        return traceId;
    }

    private String resolveRequestUser(HttpServletRequest request) {
        String authenticatedUser = resolveAuthenticatedUser(request);
        if (StringUtils.isNotBlank(authenticatedUser)) {
            return authenticatedUser;
        }
        String phone = findPhoneParameter(request);
        if (StringUtils.isNotBlank(phone)) {
            return maskPhone(phone);
        }
        if (!(request instanceof RepeatedlyRequestWrapper requestWrapper)) {
            return null;
        }
        try {
            String body = new String(requestWrapper.getBody(), requestCharset(request));
            JsonNode root = JsonUtils.getObjectMapper().readTree(body);
            return maskPhone(findPhone(root));
        } catch (Exception exception) {
            return null;
        }
    }

    private String resolveAuthenticatedUser(HttpServletRequest request) {
        try {
            Object loginId = resolveCurrentLoginId();
            if (Objects.isNull(loginId)) {
                loginId = resolveBearerLoginId(request);
            }
            if (Objects.isNull(loginId)) {
                return null;
            }
            SaSession session = StpUtil.getSessionByLoginId(loginId, false);
            if (Objects.nonNull(session)) {
                Object sessionPhone = session.get(Constants.PHONE);
                Object sessionNickName = session.get(Constants.NICK_NAME);
                if (Objects.nonNull(sessionPhone) && StringUtils.isNotBlank(sessionPhone.toString())
                        && Objects.nonNull(sessionNickName) && StringUtils.isNotBlank(sessionNickName.toString())) {
                    return buildLogUser(sessionNickName.toString(), sessionPhone.toString());
                }
            }

            Long userId = Long.valueOf(loginId.toString());
            User user = userService.getById(userId);
            if (Objects.isNull(user) || StringUtils.isBlank(user.getPhone())) {
                return null;
            }
            if (Objects.isNull(session)) {
                session = StpUtil.getSessionByLoginId(loginId, true);
            }
            if (Objects.nonNull(session)) {
                session.set(Constants.PHONE, user.getPhone());
                session.set(Constants.NICK_NAME, user.getNickName());
            }
            return buildLogUser(user.getNickName(), user.getPhone());
        } catch (Exception exception) {
            log.debug("请求日志未能解析登录用户，按匿名请求记录", exception);
            return null;
        }
    }

    private Object resolveCurrentLoginId() {
        try {
            return StpUtil.getLoginIdDefaultNull();
        } catch (Exception exception) {
            return null;
        }
    }

    private Object resolveBearerLoginId(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (StringUtils.isBlank(authorization) || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = authorization.substring(7).trim();
        return StringUtils.isBlank(token) ? null : StpUtil.getLoginIdByToken(token);
    }

    private String buildLogUser(String nickName, String phone) {
        String maskedPhone = maskPhone(phone);
        return StringUtils.isBlank(nickName) ? maskedPhone : nickName.trim() + "(" + maskedPhone + ")";
    }

    private String findPhoneParameter(HttpServletRequest request) {
        for (Map.Entry<String, String[]> parameter : request.getParameterMap().entrySet()) {
            if (!isPhoneField(parameter.getKey()) || Objects.isNull(parameter.getValue())
                    || parameter.getValue().length == 0) {
                continue;
            }
            return parameter.getValue()[0];
        }
        return null;
    }

    private String findPhone(JsonNode node) {
        if (Objects.isNull(node)) {
            return null;
        }
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                if (isPhoneField(field.getKey()) && field.getValue().isValueNode()) {
                    return field.getValue().asText();
                }
                String nestedPhone = findPhone(field.getValue());
                if (StringUtils.isNotBlank(nestedPhone)) {
                    return nestedPhone;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                String nestedPhone = findPhone(item);
                if (StringUtils.isNotBlank(nestedPhone)) {
                    return nestedPhone;
                }
            }
        }
        return null;
    }

    private void logRequestStart(HttpServletRequest request) {
        log.info("请求开始：{}", REQUEST_START);
        String url = request.getMethod() + " " + request.getRequestURI();
        if (isJsonRequest(request)) {
            log.info("开始请求 => URL[{}], 参数类型[json], 参数: [{}]", url, jsonParameters(request));
            return;
        }
        if (!request.getParameterMap().isEmpty()) {
            log.info("开始请求 => URL[{}], 参数类型[param], 参数: [{}]", url, requestParameters(request));
            return;
        }
        log.info("开始请求 => URL[{}], 无参数", url);
    }

    private String jsonParameters(HttpServletRequest request) {
        if (!(request instanceof RepeatedlyRequestWrapper requestWrapper)) {
            long contentLength = request.getContentLengthLong();
            if (contentLength > MAX_BODY_BYTES) {
                return "请求体超过 " + MAX_BODY_BYTES + " 字节，已省略";
            }
            return contentLength < 0 ? "请求体长度未知，已省略" : "无参数";
        }
        byte[] body = requestWrapper.getBody();
        if (body.length == 0) {
            return "无参数";
        }
        try {
            JsonNode root = JsonUtils.getObjectMapper().readTree(new String(body, requestCharset(request)));
            sanitizeNode(root);
            return clip(JsonUtils.toJsonString(root));
        } catch (Exception exception) {
            return "JSON 解析失败，参数已省略";
        }
    }

    private String requestParameters(HttpServletRequest request) {
        ObjectNode root = JsonUtils.getObjectMapper().createObjectNode();
        for (Map.Entry<String, String[]> parameter : request.getParameterMap().entrySet()) {
            String[] values = parameter.getValue();
            if (Objects.isNull(values) || values.length == 0) {
                root.putNull(parameter.getKey());
            } else if (values.length == 1) {
                root.put(parameter.getKey(), values[0]);
            } else {
                ArrayNode array = root.putArray(parameter.getKey());
                for (String value : values) {
                    array.add(value);
                }
            }
        }
        sanitizeNode(root);
        return clip(JsonUtils.toJsonString(root));
    }

    private void sanitizeNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            List<Map.Entry<String, JsonNode>> fields = new ArrayList<>(objectNode.properties());
            for (Map.Entry<String, JsonNode> field : fields) {
                String fieldName = field.getKey();
                JsonNode value = field.getValue();
                if (isSecretField(fieldName)) {
                    objectNode.put(fieldName, MASKED_VALUE);
                } else if (isPhoneField(fieldName) && value.isValueNode()) {
                    objectNode.put(fieldName, maskPhone(value.asText()));
                } else if (isIdentityField(fieldName) && value.isValueNode()) {
                    objectNode.put(fieldName, maskIdentity(value.asText()));
                } else {
                    sanitizeNode(value);
                }
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                sanitizeNode(item);
            }
        }
    }

    private boolean isSecretField(String fieldName) {
        String normalizedName = normalizeFieldName(fieldName);
        return normalizedName.contains("password")
                || normalizedName.contains("passwd")
                || normalizedName.equals("pwd")
                || normalizedName.contains("token")
                || normalizedName.contains("secret")
                || normalizedName.contains("apikey")
                || normalizedName.contains("accesskey")
                || normalizedName.contains("privatekey")
                || normalizedName.contains("authorization")
                || normalizedName.contains("cookie")
                || normalizedName.contains("credential")
                || normalizedName.contains("signature")
                || normalizedName.contains("verifycode")
                || normalizedName.contains("smscode")
                || normalizedName.contains("captcha");
    }

    private boolean isPhoneField(String fieldName) {
        String normalizedName = normalizeFieldName(fieldName);
        return normalizedName.contains("phone")
                || normalizedName.contains("mobile")
                || normalizedName.endsWith("tel");
    }

    private boolean isIdentityField(String fieldName) {
        String normalizedName = normalizeFieldName(fieldName);
        return normalizedName.contains("idcard")
                || normalizedName.contains("idnumber")
                || normalizedName.contains("identitycard")
                || normalizedName.contains("identityno")
                || normalizedName.contains("certno")
                || normalizedName.contains("cardno");
    }

    private String normalizeFieldName(String fieldName) {
        if (StringUtils.isBlank(fieldName)) {
            return "";
        }
        return fieldName.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    private String maskPhone(String phone) {
        if (StringUtils.isBlank(phone)) {
            return "-";
        }
        String value = phone.replaceAll("[^0-9]", "");
        if (value.length() <= 7) {
            return MASKED_VALUE;
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    private String maskIdentity(String identity) {
        if (StringUtils.isBlank(identity)) {
            return MASKED_VALUE;
        }
        String value = identity.replaceAll("[^0-9A-Za-z]", "");
        if (value.length() <= 7) {
            return MASKED_VALUE;
        }
        return value.substring(0, 3) + "********" + value.substring(value.length() - 4);
    }

    private String clip(String text) {
        if (StringUtils.isBlank(text) || text.length() <= MAX_PARAMETER_TEXT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_PARAMETER_TEXT_LENGTH) + "...[参数已截断]";
    }

    private Charset requestCharset(HttpServletRequest request) {
        String characterEncoding = request.getCharacterEncoding();
        return StringUtils.isBlank(characterEncoding)
                ? StandardCharsets.UTF_8 : Charset.forName(characterEncoding);
    }

    private boolean isJsonRequest(HttpServletRequest request) {
        return StringUtils.startWithAnyIgnoreCase(request.getContentType(), MediaType.APPLICATION_JSON_VALUE);
    }

    private void logRequestEnd(HttpServletRequest request,
                               HttpServletResponse response,
                               long startedAt,
                               Throwable requestFailure) {
        String url = request.getMethod() + " " + request.getRequestURI();
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        String markedLevel = Objects.toString(request.getAttribute(Constants.REQUEST_LOG_LEVEL), "");
        if (Objects.nonNull(requestFailure) || Constants.LOG_LEVEL_ERROR.equals(markedLevel)
                || response.getStatus() >= 500) {
            String exceptionName = Objects.nonNull(requestFailure)
                    ? requestFailure.getClass().getSimpleName() : "-";
            log.error("结束请求 => URL[{}], 状态[{}], 耗时[{} ms], 异常[{}]",
                    url, response.getStatus(), durationMs, exceptionName, requestFailure);
            log.error("请求结束：{}", REQUEST_END);
            return;
        }
        if (Constants.LOG_LEVEL_WARN.equals(markedLevel) || response.getStatus() >= 400) {
            log.warn("结束请求 => URL[{}], 状态[{}], 耗时[{} ms]", url, response.getStatus(), durationMs);
            log.warn("请求结束：{}", REQUEST_END);
            return;
        }
        log.info("结束请求 => URL[{}], 状态[{}], 耗时[{} ms]", url, response.getStatus(), durationMs);
        log.info("请求结束：{}", REQUEST_END);
    }
}
