package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.DashboardHomeResp;
import com.awe.apex.quant.domain.dto.DashboardResp;

/**
 * 仪表盘
 */
public interface IDashboardService {

    /**
     * 汇总（完整/绩效向，供模拟盘等复用）
     *
     * @param accountId 账户，可空
     * @return 仪表盘
     */
    DashboardResp overview(Long accountId);

    /**
     * 决策看板首页聚合
     *
     * @param accountId 账户，可空
     * @param groupName 自选分组，可空
     * @return 首页
     */
    DashboardHomeResp home(Long accountId, String groupName);
}
