package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.bo.DashboardCommandContextBO;
import com.awe.apex.quant.domain.dto.DashboardCommandResp;

/**
 * 看板盘前指挥规则服务。
 */
public interface IDashboardCommandService {

    /**
     * 根据已有市场和用户数据生成盘前总结与今日操作指引。
     *
     * @param context 指挥规则计算上下文
     * @return 盘前总结与今日操作指引
     */
    DashboardCommandResp build(DashboardCommandContextBO context);
}
