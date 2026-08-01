package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.MyHoldingSaveReq;
import com.awe.apex.quant.domain.entity.MyHolding;

import java.util.List;
import java.util.Map;

/**
 * 我的持仓（手动维护）
 */
public interface IMyHoldingService {

    /**
     * 持仓列表（附带现价/浮盈亏）
     *
     * @return 列表
     */
    List<MyHolding> listHoldings();

    /**
     * 新增或更新持仓（同代码合并更新）
     *
     * @param req 请求
     * @return 持仓
     */
    MyHolding save(MyHoldingSaveReq req);

    /**
     * 删除持仓
     *
     * @param id 主键
     */
    void remove(Long id);

    /**
     * 刷新持仓行情（缺报价优先），并返回最新列表
     *
     * @param onlyMissing 是否只刷本地无现价的
     * @return 结果（含 holdings）
     */
    Map<String, Object> refreshQuotes(Boolean onlyMissing);
}
