package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.UserActivityReq;
import com.awe.apex.quant.domain.dto.UserUsageOverviewResp;

/**
 * 用户使用情况服务。
 */
public interface UserUsageService {

    /**
     * 记录用户登录事件。
     *
     * @param userId 用户ID
     */
    void recordLogin(Long userId);

    /**
     * 记录当前用户页面访问事件。
     *
     * @param req 页面访问请求
     */
    void recordPageView(UserActivityReq req);

    /**
     * 查询管理员用户使用情况总览。
     *
     * @param days 统计周期天数
     * @return 用户使用情况总览
     */
    UserUsageOverviewResp overview(int days);
}
