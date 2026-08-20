package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.entity.PortfolioIntradaySnapshot;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 组合盘中收益快照服务
 */
public interface IPortfolioIntradayService {

    /**
     * 写入组合五分钟盘中快照
     *
     * @param portfolioId 组合ID
     * @param snapshotTime 快照时间
     * @return 快照
     */
    PortfolioIntradaySnapshot snapshot(Long portfolioId, LocalDateTime snapshotTime);

    /**
     * 为当前用户全部活跃组合写入盘中快照
     *
     * @param snapshotTime 快照时间
     * @return 成功数量
     */
    int snapshotAll(LocalDateTime snapshotTime);

    /**
     * 查询组合指定交易日的盘中收益序列
     *
     * @param portfolioId 组合ID
     * @param tradeDate   交易日，为空时取当天
     * @return 盘中快照序列
     */
    List<PortfolioIntradaySnapshot> list(Long portfolioId, LocalDate tradeDate);
}
