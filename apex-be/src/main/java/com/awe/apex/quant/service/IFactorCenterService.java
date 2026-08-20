package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.FactorCenterResp;

/**
 * 因子中心服务
 */
public interface IFactorCenterService {

    /**
     * 查询个股六类因子与 Alpha 评分。
     *
     * @param code 证券代码
     * @return 因子中心详情
     */
    FactorCenterResp query(String code);
}
