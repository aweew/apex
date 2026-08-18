package com.awe.apex.quant.service;

import com.awe.apex.common.api.PageResponse;
import com.awe.apex.quant.domain.dto.TradeRecordResp;
import com.awe.apex.quant.domain.entity.JournalTrade;
import com.awe.apex.quant.domain.entity.Portfolio;
import com.awe.apex.quant.domain.enums.PortfolioTradeSourceEnum;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 共享组合交易流水服务。
 */
public interface IPortfolioTradeRecordService {

    /**
     * 根据持仓数量变化生成交易流水。
     *
     * @param portfolio     组合
     * @param code          证券代码
     * @param stockName     证券简称
     * @param beforeQuantity 变动前数量
     * @param afterQuantity 变动后数量
     * @param reportedPrice 用户或 Bot 提供的成交价
     * @param tradeTime     成交时间
     * @param source        变动来源
     * @param sourceRef     来源幂等引用
     * @return 新建或已存在的流水，数量未变化时返回空
     */
    JournalTrade recordChange(Portfolio portfolio, String code, String stockName,
                              Integer beforeQuantity, Integer afterQuantity,
                              BigDecimal reportedPrice, LocalDateTime tradeTime,
                              PortfolioTradeSourceEnum source, String sourceRef);

    /**
     * 分页查询共享组合交易记录。
     *
     * @param portfolioId 组合ID
     * @param code        证券代码
     * @param side        交易方向
     * @param source      记录来源
     * @param page        页码
     * @param size        每页条数
     * @return 分页记录
     */
    PageResponse<TradeRecordResp> page(Long portfolioId, String code, String side, String source,
                                       Integer page, Integer size);

    /**
     * 查询共享组合指定证券的 K 线交易标记。
     *
     * @param code 证券代码
     * @return 交易标记
     */
    List<TradeRecordResp> listMarkers(String code);
}
