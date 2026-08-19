package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.BarSyncReq;
import com.awe.apex.quant.domain.dto.BarSyncResp;
import com.awe.apex.quant.domain.dto.FillBarsResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 日线服务
 */
public interface IBarDailyService extends IService<BarDaily> {

    /**
     * 同步日线并落库
     *
     * @param req 同步请求
     * @return 同步结果
     */
    BarSyncResp syncBars(BarSyncReq req);

    /**
     * 按自选分组同步日线（服务端分批，默认上限 80）
     *
     * @param groupName 分组名
     * @param beginDate 开始日期，可空
     * @param endDate   结束日期，可空
     * @return 同步结果
     */
    BarSyncResp syncWatchlistGroup(String groupName, String beginDate, String endDate);

    /**
     * 仅同步缺失或过期日线的自选
     *
     * @param groupName 分组
     * @param limit     上限
     * @return 同步结果
     */
    BarSyncResp syncStaleWatchlist(String groupName, Integer limit);

    /**
     * 同步指定代码中缺失或过期的日线，代码会全局去重并分批处理。
     *
     * @param codes 证券代码
     * @return 同步结果
     */
    BarSyncResp syncStaleCodes(List<String> codes);

    /**
     * 多轮补齐自选缺失/过期日线
     *
     * @param groupName 分组
     * @param rounds    轮数
     * @param limit     每轮上限
     * @return 汇总
     */
    FillBarsResp fillWatchlist(String groupName, Integer rounds, Integer limit);
}
