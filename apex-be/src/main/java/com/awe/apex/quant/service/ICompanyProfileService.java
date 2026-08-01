package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.CompanyProfileResp;

/**
 * 公司概况服务
 */
public interface ICompanyProfileService {

    /**
     * 查询公司概况（本地优先；缺失或 forceRefresh 时拉取东财）
     *
     * @param code         证券代码
     * @param forceRefresh 是否强制刷新
     * @return 概况
     */
    CompanyProfileResp query(String code, boolean forceRefresh);
}
