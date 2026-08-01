package com.awe.apex.common.interceptor;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.map.MapUtil;
import com.alibaba.ttl.TransmittableThreadLocal;
import com.awe.apex.common.filter.RepeatedlyRequestWrapper;
import com.awe.apex.common.util.JsonUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.io.BufferedReader;
import java.util.Arrays;
import java.util.Map;

/**
 * web调用时间耗时统计拦截器
 *
 * @author Awe
 * @since 2025/12/8 15:56
 */
@RequiredArgsConstructor
@Slf4j
@Service
public class WebInvokeTimeInterceptor implements HandlerInterceptor {

    /**
     * 排除敏感属性字段
     */
    public static final String[] EXCLUDE_PROPERTIES = {"password", "oldPassword", "newPassword", "confirmPassword"};
    public static final String[] EXCLUDE_URL = {""};

    private final TransmittableThreadLocal<StopWatch> invokeTimeTL = new TransmittableThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String url = request.getMethod() + " " + request.getRequestURI();

        boolean excluded = Arrays.stream(EXCLUDE_URL).anyMatch(url::contains);
        if (!excluded) {
            return true;
        }

        // 打印请求参数
        if (isJsonRequest(request)) {
            String jsonParam = "";
            if (request instanceof RepeatedlyRequestWrapper) {
                BufferedReader reader = request.getReader();
                jsonParam = IoUtil.read(reader);
            }
            if (log.isDebugEnabled()) {
                log.debug("\n====================[请求开始]====================\n 开始请求 => URL[{}], 参数类型[json], 参数: [{}]", url, jsonParam);
            }

        } else {
            Map<String, String[]> parameterMap = request.getParameterMap();
            if (MapUtil.isNotEmpty(parameterMap)) {
                String parameters = JsonUtils.toJsonString(parameterMap);
                if (log.isDebugEnabled()) {
                    log.debug("\n====================[请求开始]====================\n 开始请求 => URL[{}], 参数类型[param], 参数: [{}]", url, parameters);
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("\n====================[请求开始]====================\n 开始请求 => URL[{}], 无参数", url);
                }
            }
        }
        StopWatch stopWatch = new StopWatch();
        invokeTimeTL.set(stopWatch);
        stopWatch.start();
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        StopWatch stopWatch = invokeTimeTL.get();
        if (stopWatch != null) {
            stopWatch.stop();
            if (log.isDebugEnabled()) {
                log.debug("结束请求 => URL[{}], 耗时: [{} ms]\n====================[请求结束]====================\n", request.getMethod() + " " + request.getRequestURI(), stopWatch.getTime());
            }
            invokeTimeTL.remove();
        }
    }

    /**
     * 判断本次请求的数据类型是否为json
     *
     * @param request request
     * @return boolean
     */
    private boolean isJsonRequest(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType != null) {
            return StringUtils.startsWithIgnoreCase(contentType, MediaType.APPLICATION_JSON_VALUE);
        }
        return false;
    }

}
