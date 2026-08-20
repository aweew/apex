package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.CapitalFlowOverviewResp;

/**
 * 资金面与龙虎榜服务。
 */
public interface ICapitalFlowService {

    /**
     * 查询最新资金面总览。
     *
     * @param limit 每类榜单条数
     * @return 资金面总览
     */
    CapitalFlowOverviewResp overview(Integer limit);

    /**
     * 同步指定资金面数据并返回最新总览。
     *
     * @param mode 同步模式flow、lhb或all
     * @param limit 每类榜单条数
     * @return 最新资金面总览
     */
    CapitalFlowOverviewResp refresh(String mode, Integer limit);
}
