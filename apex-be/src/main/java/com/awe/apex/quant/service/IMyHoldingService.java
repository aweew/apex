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
     * 查询持仓及最新行情，不加载题材、技术和估值信息
     *
     * @return 轻量持仓列表
     */
    List<MyHolding> listHoldingsLite();

    /**
     * 查询持仓证券代码，不加载行情、技术和估值信息
     *
     * @return 持仓证券代码
     */
    List<String> listHoldingCodes();

    /**
     * 对给定持仓行做行情/题材/技术/估值 enrich（不读写库）
     *
     * @param list 持仓行（需含 code/quantity/cost 等）
     * @return 同一列表（已填充展示字段）
     */
    List<MyHolding> enrichHoldings(List<MyHolding> list);

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

    /**
     * 按代码列表刷新行情到 stock_basic（供组合等复用）
     *
     * @param codes       代码
     * @param onlyMissing 是否只刷本地无现价的
     * @return success/fail/message
     */
    Map<String, Object> refreshQuotesForCodes(List<String> codes, Boolean onlyMissing);
}
