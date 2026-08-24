package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.StockDetailResp;
import com.awe.apex.quant.domain.dto.StockIntradayResp;
import com.awe.apex.quant.domain.dto.StockSearchItem;
import com.awe.apex.quant.domain.entity.StockBasic;

import java.util.List;

/**
 * 股票基本信息服务
 */
public interface IStockService {

    /**
     * 同步并落库基本信息
     *
     * @param code 证券代码
     * @return 基本信息
     */
    StockBasic syncBasic(String code);

    /**
     * 同步并落库实时行情，不补估值和行业资料。
     *
     * @param code 证券代码
     * @return 实时行情
     */
    StockBasic syncQuote(String code);

    /**
     * 查询详情（默认只读本地；refresh=true 时才同步外网基本信息，不自动拉日线）
     *
     * @param code      证券代码
     * @param barLimit  K 线条数
     * @param refresh   是否强制刷新基本信息
     * @return 详情
     */
    StockDetailResp detail(String code, Integer barLimit, Boolean refresh);

    /**
     * 查询分时（东财实时/最近交易日）
     *
     * @param code 证券代码
     * @return 分时
     */
    StockIntradayResp intraday(String code);

    /**
     * 按代码/名称搜索
     *
     * @param keyword 关键词
     * @param limit   条数
     * @return 结果
     */
    List<StockSearchItem> search(String keyword, Integer limit);
}
