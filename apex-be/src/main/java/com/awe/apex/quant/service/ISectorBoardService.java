package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.SectorBoardItem;
import com.awe.apex.quant.domain.dto.SectorBoardResp;
import com.awe.apex.quant.domain.dto.SectorConstituentResp;
import com.awe.apex.quant.domain.dto.SectorRefreshResp;
import com.awe.apex.quant.domain.dto.SectorRotationResp;

import java.util.List;

/**
 * 板块看板服务
 */
public interface ISectorBoardService {

    /**
     * 板块榜单
     *
     * @param boardType 类型
     * @param sortBy    排序字段
     * @param order     排序方向
     * @param limit     条数
     * @param tradeDate 交易日，可空表示最新
     * @return 榜单
     */
    SectorBoardResp board(String boardType, String sortBy, String order, Integer limit, String tradeDate);

    /**
     * 成分股列表
     *
     * @param code      板块代码
     * @param boardType 类型
     * @param sortBy    排序字段
     * @param order     排序方向
     * @param tradeDate 交易日，可空表示最新
     * @return 成分股
     */
    SectorConstituentResp constituents(String code, String boardType, String sortBy, String order, String tradeDate);

    /**
     * 刷新榜单
     *
     * @param types 类型逗号分隔
     * @return 结果
     */
    SectorRefreshResp refresh(String types);

    /**
     * 刷新成分股
     *
     * @param code      板块代码
     * @param boardType 类型
     * @return 结果
     */
    SectorRefreshResp refreshConstituents(String code, String boardType);

    /**
     * 主线识别（行业+概念+题材综合评分）
     *
     * @param tradeDate 交易日
     * @param limit     条数
     * @return 主线列表
     */
    List<SectorBoardItem> mainline(String tradeDate, Integer limit);

    /**
     * 板块轮动时间轴（按日 Top）
     *
     * @param boardType 类型
     * @param days      天数
     * @param topN      每日 Top
     * @return 时间轴
     */
    SectorRotationResp rotation(String boardType, Integer days, Integer topN);
}
