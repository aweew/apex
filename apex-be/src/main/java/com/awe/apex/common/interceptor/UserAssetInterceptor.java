package com.awe.apex.common.interceptor;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.service.ApexUserAuthService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 模拟账户归属校验
 */
@Component
public class UserAssetInterceptor implements HandlerInterceptor {

    @Resource
    private ApexUserAuthService apexUserAuthService;

    /**
     * 拒绝指定其他用户模拟账户的请求
     *
     * @param request 请求
     * @param response 响应
     * @param handler 处理器
     * @return 是否继续
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String accountId = request.getParameter("accountId");
        if (accountId != null && !accountId.isBlank()
                && !String.valueOf(apexUserAuthService.currentPaperAccountId()).equals(accountId)) {
            throw new BusinessException("无权访问该模拟账户");
        }
        return true;
    }
}
