package com.awe.apex.common.interceptor;

import cn.dev33.satoken.stp.StpUtil;
import com.awe.apex.common.constant.Constants;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.UUID;

/**
 * 日志拦截器
 *
 * @author Awe
 * @since 2023/11/22 11:38
 */
@Slf4j
public class LogInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {

        // 初始化traceId
        String traceId = request.getHeader(Constants.TRACE_ID);
        if (StringUtils.isBlank(traceId)) {
            traceId = UUID.randomUUID().toString().trim().replaceAll("-", "").substring(0, 16);
        }
        MDC.put(Constants.TRACE_ID, traceId);
        response.addHeader(Constants.TRACE_ID, traceId);

        // todo 添加客户端类型等信息

        // 记录当前登录人员信息
        String userId = request.getHeader(Constants.USER_ID);
        if (StringUtils.isEmpty(userId)) {
            try {
                userId = StpUtil.getLoginIdAsString();
            } catch (Exception e) {
            }
        }

        MDC.put(Constants.USER_ID, userId);
        response.addHeader(Constants.USER_ID, userId);
        return HandlerInterceptor.super.preHandle(request, response, handler);
    }

    @Override
    public void postHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, Exception ex) throws Exception {
        MDC.remove(Constants.TRACE_ID);

        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }

}
