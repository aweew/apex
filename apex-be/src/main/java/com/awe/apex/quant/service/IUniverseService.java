package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.UniverseRefreshReq;
import com.awe.apex.quant.domain.dto.UniverseRefreshResp;
import com.awe.apex.quant.domain.entity.UniverseSnapshot;

import java.util.List;

/**
 * 股票池服务
 */
public interface IUniverseService {

    /**
     * 刷新股票池
     *
     * @param req 请求
     * @return 结果
     */
    UniverseRefreshResp refresh(UniverseRefreshReq req);

    /**
     * 最新批次列表
     *
     * @return 列表
     */
    List<UniverseSnapshot> latest();
}
