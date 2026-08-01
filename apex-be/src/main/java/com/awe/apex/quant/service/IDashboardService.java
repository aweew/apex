package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.DashboardResp;

/**
 * 仪表盘
 */
public interface IDashboardService {

    /**
     * 汇总
     *
     * @param accountId 账户，可空
     * @return 仪表盘
     */
    DashboardResp overview(Long accountId);
}
