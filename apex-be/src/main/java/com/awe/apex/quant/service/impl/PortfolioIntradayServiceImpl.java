package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.PortfolioSummaryResp;
import com.awe.apex.quant.domain.entity.Portfolio;
import com.awe.apex.quant.domain.entity.PortfolioIntradaySnapshot;
import com.awe.apex.quant.mapper.PortfolioIntradaySnapshotMapper;
import com.awe.apex.quant.mapper.PortfolioMapper;
import com.awe.apex.quant.service.IPortfolioIntradayService;
import com.awe.apex.quant.service.IPortfolioService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 组合盘中收益快照服务实现
 */
@Slf4j
@Service
public class PortfolioIntradayServiceImpl implements IPortfolioIntradayService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    @Resource
    private ApexUserContext userContext;

    @Resource
    private PortfolioMapper portfolioMapper;

    @Resource
    private PortfolioIntradaySnapshotMapper snapshotMapper;

    @Resource
    private IPortfolioService portfolioService;

    /**
     * 写入组合五分钟盘中快照
     *
     * @param portfolioId 组合ID
     * @param snapshotTime 快照时间
     * @return 快照
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PortfolioIntradaySnapshot snapshot(Long portfolioId, LocalDateTime snapshotTime) {
        if (Objects.isNull(portfolioId) || Objects.isNull(snapshotTime)) {
            throw new BusinessException("组合ID和快照时间不能为空");
        }
        Long currentUserId = currentUserId();
        Portfolio portfolio = portfolioMapper.selectById(portfolioId);
        if (Objects.isNull(portfolio)) {
            throw new BusinessException("组合不存在");
        }
        if (!Objects.equals(portfolio.getUserId(), currentUserId)) {
            throw new BusinessException("无权写入该组合盘中快照");
        }

        LocalDateTime bucketTime = snapshotTime.withSecond(0).withNano(0)
                .withMinute(snapshotTime.getMinute() / 5 * 5);
        PortfolioSummaryResp summary = portfolioService.intradaySummary(portfolioId);
        PortfolioIntradaySnapshot exist = snapshotMapper.selectOne(
                Wrappers.<PortfolioIntradaySnapshot>lambdaQuery()
                        .eq(PortfolioIntradaySnapshot::getPortfolioId, portfolioId)
                        .eq(PortfolioIntradaySnapshot::getSnapshotTime, bucketTime)
                        .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (Objects.nonNull(exist)) {
            exist.setTotalEquity(summary.getTotalEquity());
            exist.setTodayPnl(summary.getTodayPnl());
            exist.setTodayPct(summary.getTodayPct());
            exist.setPositionCount(summary.getPositionCount());
            exist.setUpdateTime(now);
            snapshotMapper.updateById(exist);
            return exist;
        }

        PortfolioIntradaySnapshot created = PortfolioIntradaySnapshot.builder()
                .portfolioId(portfolioId)
                .tradeDate(bucketTime.toLocalDate())
                .snapshotTime(bucketTime)
                .totalEquity(summary.getTotalEquity())
                .todayPnl(summary.getTodayPnl())
                .todayPct(summary.getTodayPct())
                .positionCount(summary.getPositionCount())
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
        snapshotMapper.insert(created);
        return created;
    }

    /**
     * 为当前用户全部活跃组合写入盘中快照
     *
     * @param snapshotTime 快照时间
     * @return 成功数量
     */
    @Override
    public int snapshotAll(LocalDateTime snapshotTime) {
        Long currentUserId = currentUserId();
        List<Portfolio> portfolios = portfolioMapper.selectList(Wrappers.<Portfolio>lambdaQuery()
                .eq(Portfolio::getUserId, currentUserId)
                .eq(Portfolio::getStatus, STATUS_ACTIVE));
        int successCount = 0;
        for (Portfolio portfolio : portfolios) {
            try {
                snapshot(portfolio.getId(), snapshotTime);
                successCount++;
            } catch (Exception ex) {
                log.warn("组合盘中快照写入失败，用户编号={}，组合编号={}，快照时间={}，原因={}",
                        currentUserId, portfolio.getId(), snapshotTime, ex.getMessage());
            }
        }
        return successCount;
    }

    /**
     * 查询组合指定交易日的盘中收益序列
     *
     * @param portfolioId 组合ID
     * @param tradeDate   交易日，为空时取当天
     * @return 盘中快照序列
     */
    @Override
    public List<PortfolioIntradaySnapshot> list(Long portfolioId, LocalDate tradeDate) {
        if (Objects.isNull(portfolioId)) {
            throw new BusinessException("组合ID不能为空");
        }
        currentUserId();
        Portfolio portfolio = portfolioMapper.selectById(portfolioId);
        if (Objects.isNull(portfolio)) {
            throw new BusinessException("组合不存在");
        }
        LocalDate queryDate = Objects.nonNull(tradeDate) ? tradeDate : LocalDate.now();
        return snapshotMapper.selectList(Wrappers.<PortfolioIntradaySnapshot>lambdaQuery()
                .eq(PortfolioIntradaySnapshot::getPortfolioId, portfolioId)
                .eq(PortfolioIntradaySnapshot::getTradeDate, queryDate)
                .orderByAsc(PortfolioIntradaySnapshot::getSnapshotTime));
    }

    private Long currentUserId() {
        Long currentUserId = userContext.currentUserIdOrNull();
        if (Objects.isNull(currentUserId)) {
            throw new BusinessException("未获取到当前用户");
        }
        return currentUserId;
    }
}
