package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.PortfolioHoldingSaveReq;
import com.awe.apex.quant.domain.dto.PortfolioImportReq;
import com.awe.apex.quant.domain.dto.PortfolioImportResp;
import com.awe.apex.quant.domain.dto.PortfolioOrderReq;
import com.awe.apex.quant.domain.dto.PortfolioSaveReq;
import com.awe.apex.quant.domain.dto.PortfolioSummaryResp;
import com.awe.apex.quant.domain.entity.MyHolding;
import com.awe.apex.quant.domain.entity.Portfolio;
import com.awe.apex.quant.domain.entity.PortfolioDaily;
import com.awe.apex.quant.domain.entity.PortfolioHolding;
import com.awe.apex.quant.domain.enums.PortfolioTradeSourceEnum;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 实盘组合服务
 */
public interface IPortfolioService {

    /**
     * 确保默认组合存在，并完成 my_holding 首次迁移
     *
     * @return 默认组合
     */
    Portfolio ensureDefaultPortfolio();

    /**
     * 组合列表（含今日浮盈摘要）
     *
     * @param includeArchived 是否含归档
     * @return 列表
     */
    List<PortfolioSummaryResp> listPortfolios(boolean includeArchived);

    /**
     * 保存组合（新建/改名/归档）
     *
     * @param req 请求
     * @return 组合
     */
    Portfolio savePortfolio(PortfolioSaveReq req);

    /**
     * 保存组合展示顺序
     *
     * @param req 排序请求
     */
    void sortPortfolios(PortfolioOrderReq req);

    /**
     * 删除组合（禁止删默认）
     *
     * @param id 组合ID
     */
    void removePortfolio(Long id);

    /**
     * 组合详情
     *
     * @param id 组合ID
     * @return 详情
     */
    PortfolioSummaryResp detail(Long id);

    /**
     * 保存持仓
     *
     * @param portfolioId 组合ID
     * @param req         请求
     * @return 持仓
     */
    PortfolioHolding saveHolding(Long portfolioId, PortfolioHoldingSaveReq req);

    /**
     * 按指定来源保存持仓并生成交易流水。
     *
     * @param portfolioId 组合ID
     * @param req         请求
     * @param source      变动来源
     * @param sourceRef   来源幂等引用
     * @return 持仓
     */
    PortfolioHolding saveHolding(Long portfolioId, PortfolioHoldingSaveReq req,
                                 PortfolioTradeSourceEnum source, String sourceRef);

    /**
     * 删除持仓
     *
     * @param portfolioId 组合ID
     * @param holdingId   持仓ID
     */
    void removeHolding(Long portfolioId, Long holdingId);

    /**
     * 按指定来源删除持仓并生成清仓流水。
     *
     * @param portfolioId 组合ID
     * @param holdingId   持仓ID
     * @param source      变动来源
     * @param sourceRef   来源幂等引用
     */
    void removeHolding(Long portfolioId, Long holdingId,
                       PortfolioTradeSourceEnum source, String sourceRef);

    /**
     * 文本导入持仓
     *
     * @param portfolioId 组合ID
     * @param req         文本
     * @return 结果
     */
    PortfolioImportResp importHoldings(Long portfolioId, PortfolioImportReq req);

    /**
     * 打当日快照
     *
     * @param portfolioId 组合ID
     * @return 快照
     */
    PortfolioDaily snapshot(Long portfolioId);

    /**
     * 全部活跃组合打快照
     *
     * @return 成功数
     */
    int snapshotAll();

    /**
     * 日收益序列
     *
     * @param portfolioId 组合ID
     * @param days        近 N 日
     * @return 列表
     */
    List<PortfolioDaily> listDaily(Long portfolioId, Integer days);

    /**
     * 刷新组合持仓行情（写入 stock_basic 后详情可 enrich）
     *
     * @param portfolioId 组合ID
     * @param onlyMissing 是否只刷缺现价的
     * @return 结果
     */
    Map<String, Object> refreshQuotes(Long portfolioId, Boolean onlyMissing);

    /**
     * 一键刷新全部活跃组合行情（代码去重）
     *
     * @param onlyMissing 是否只刷缺现价的
     * @return 结果
     */
    Map<String, Object> refreshQuotesAll(Boolean onlyMissing);

    /**
     * 我的持仓变更后同步到默认组合（双写）
     *
     * @param holding 持仓
     */
    void mirrorMyHoldingSave(MyHolding holding);

    /**
     * 我的持仓变更后携带成交信息同步到默认组合。
     *
     * @param holding   持仓
     * @param tradePrice 实际成交价
     * @param tradeTime 实际成交时间
     */
    void mirrorMyHoldingSave(MyHolding holding, BigDecimal tradePrice, LocalDateTime tradeTime);

    /**
     * 我的持仓删除后同步默认组合
     *
     * @param code 证券代码
     */
    void mirrorMyHoldingRemove(String code);
}
