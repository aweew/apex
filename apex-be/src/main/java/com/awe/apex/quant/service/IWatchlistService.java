package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.CorrelationMatrixResp;
import com.awe.apex.quant.domain.dto.WatchlistAddReq;
import com.awe.apex.quant.domain.dto.WatchlistAddResp;
import com.awe.apex.quant.domain.dto.WatchlistImportReq;
import com.awe.apex.quant.domain.dto.WatchlistImportResp;
import com.awe.apex.quant.domain.dto.WatchlistMoverResp;
import com.awe.apex.quant.domain.dto.WatchlistResp;
import com.awe.apex.quant.domain.entity.Watchlist;
import com.baomidou.mybatisplus.extension.service.IService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 自选股服务
 */
public interface IWatchlistService extends IService<Watchlist> {

    /**
     * 查询自选列表
     *
     * @param groupName 分组，可空
     * @return 自选列表
     */
    List<WatchlistResp> listWatchlist(String groupName);

    /**
     * 从妙想导出文件导入自选
     *
     * @param req 导入请求
     * @return 导入结果
     */
    WatchlistImportResp importFromMxFile(WatchlistImportReq req);

    /**
     * 批量加入自选（热点/决策等入口）
     *
     * @param req 请求
     * @return 结果
     */
    WatchlistAddResp addCodes(WatchlistAddReq req);

    /**
     * 刷新分组行情快照（名称/现价/估值）
     *
     * @param groupName 分组
     * @param limit     本批上限，可空
     * @param preferMissing 优先无估值的股票
     * @return 成功/失败数量
     */
    Map<String, Object> refreshQuotes(String groupName, Integer limit, Boolean preferMissing);

    /**
     * 多轮补齐行情覆盖
     *
     * @param groupName 分组
     * @param rounds    轮数
     * @param limit     每轮上限
     * @return 汇总
     */
    Map<String, Object> fillQuotes(String groupName, Integer rounds, Integer limit);

    /**
     * 自选异动（涨跌超阈值）
     *
     * @param groupName 分组
     * @param threshold 涨跌幅阈值（%）
     * @param limit     每侧条数
     * @return 异动
     */
    WatchlistMoverResp movers(String groupName, BigDecimal threshold, Integer limit);

    /**
     * 自选日收益相关性（取涨幅前列）
     *
     * @param groupName 分组
     * @param limit     标的数
     * @param lookback  回看交易日
     * @return 矩阵
     */
    CorrelationMatrixResp correlation(String groupName, Integer limit, Integer lookback);
}
