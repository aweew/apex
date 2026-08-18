package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.api.PageResponse;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.TradeRecordResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.JournalTrade;
import com.awe.apex.quant.domain.entity.Portfolio;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.enums.PortfolioTradeChangeTypeEnum;
import com.awe.apex.quant.domain.enums.PortfolioTradePriceSourceEnum;
import com.awe.apex.quant.domain.enums.PortfolioTradeSideEnum;
import com.awe.apex.quant.domain.enums.PortfolioTradeSourceEnum;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.JournalTradeMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.market.MarketCodeUtils;
import com.awe.apex.quant.service.IPortfolioTradeRecordService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 用户组合交易流水服务实现。
 */
@Slf4j
@Service
public class PortfolioTradeRecordServiceImpl implements IPortfolioTradeRecordService {

    @Resource
    private JournalTradeMapper journalTradeMapper;

    @Resource
    private StockBasicMapper stockBasicMapper;

    @Resource
    private BarDailyMapper barDailyMapper;

    @Resource
    private ApexUserContext userContext;

    /**
     * 根据持仓数量变化生成交易流水。
     *
     * @param portfolio      组合
     * @param code           证券代码
     * @param stockName      证券简称
     * @param beforeQuantity 变动前数量
     * @param afterQuantity  变动后数量
     * @param reportedPrice  用户或 Bot 提供的成交价
     * @param tradeTime      成交时间
     * @param source         变动来源
     * @param sourceRef      来源幂等引用
     * @return 新建或已存在的流水，数量未变化时返回空
     */
    @Override
    public JournalTrade recordChange(Portfolio portfolio, String code, String stockName,
                                     Integer beforeQuantity, Integer afterQuantity,
                                     BigDecimal reportedPrice, LocalDateTime tradeTime,
                                     PortfolioTradeSourceEnum source, String sourceRef) {
        if (Objects.isNull(portfolio) || Objects.isNull(portfolio.getId())) {
            throw new BusinessException("交易流水缺少组合");
        }
        String normalizedCode = MarketCodeUtils.normalizeHoldingCode(code);
        if (StringUtils.isBlank(normalizedCode)) {
            throw new BusinessException("交易流水证券代码无效");
        }
        int before = Objects.nonNull(beforeQuantity) ? Math.max(0, beforeQuantity) : 0;
        int after = Objects.nonNull(afterQuantity) ? Math.max(0, afterQuantity) : 0;
        if (before == after) {
            return null;
        }
        PortfolioTradeSourceEnum recordSource = Objects.nonNull(source)
                ? source : PortfolioTradeSourceEnum.PORTFOLIO_WEB;
        Long currentUserId = userContext.currentUserId();
        JournalTrade existing = findBySourceRef(currentUserId, normalizedCode, recordSource, sourceRef);
        if (Objects.nonNull(existing)) {
            if (!Objects.equals(existing.getBeforeQuantity(), before)
                    || !Objects.equals(existing.getAfterQuantity(), after)) {
                throw new BusinessException("重复请求的持仓变动内容不一致");
            }
            return existing;
        }

        LocalDateTime actualTradeTime = Objects.nonNull(tradeTime) ? tradeTime : LocalDateTime.now();
        StockBasic stockBasic = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                .eq(StockBasic::getCode, normalizedCode)
                .last("LIMIT 1"));
        BigDecimal tradePrice = positivePrice(reportedPrice);
        PortfolioTradePriceSourceEnum priceSource;
        boolean estimated;
        if (Objects.nonNull(tradePrice)) {
            priceSource = recordSource == PortfolioTradeSourceEnum.WECHAT_BOT
                    ? PortfolioTradePriceSourceEnum.BOT_REPORTED
                    : PortfolioTradePriceSourceEnum.USER_REPORTED;
            estimated = false;
        } else {
            tradePrice = Objects.nonNull(stockBasic) ? positivePrice(stockBasic.getLatestPrice()) : null;
            priceSource = PortfolioTradePriceSourceEnum.MARKET_SNAPSHOT;
            estimated = true;
        }
        if (Objects.isNull(tradePrice)) {
            BarDaily recentBar = barDailyMapper.selectOne(Wrappers.<BarDaily>lambdaQuery()
                    .eq(BarDaily::getCode, normalizedCode)
                    .le(BarDaily::getTradeDate, actualTradeTime.toLocalDate())
                    .orderByDesc(BarDaily::getTradeDate)
                    .last("LIMIT 1"));
            tradePrice = Objects.nonNull(recentBar) ? positivePrice(recentBar.getClosePrice()) : null;
            priceSource = Objects.nonNull(tradePrice)
                    ? PortfolioTradePriceSourceEnum.DAILY_CLOSE
                    : PortfolioTradePriceSourceEnum.UNAVAILABLE;
        }

        PortfolioTradeSideEnum side = after > before
                ? PortfolioTradeSideEnum.BUY : PortfolioTradeSideEnum.SELL;
        PortfolioTradeChangeTypeEnum changeType;
        if (before == 0) {
            changeType = PortfolioTradeChangeTypeEnum.OPEN;
        } else if (after == 0) {
            changeType = PortfolioTradeChangeTypeEnum.CLEAR;
        } else if (after > before) {
            changeType = PortfolioTradeChangeTypeEnum.ADD;
        } else {
            changeType = PortfolioTradeChangeTypeEnum.REDUCE;
        }
        int changedQuantity = Math.abs(after - before);
        BigDecimal amount = Objects.nonNull(tradePrice)
                ? tradePrice.multiply(BigDecimal.valueOf(changedQuantity)).setScale(2, RoundingMode.HALF_UP)
                : null;
        LocalDateTime now = LocalDateTime.now();
        JournalTrade trade = JournalTrade.builder()
                .userId(currentUserId)
                .portfolioId(portfolio.getId())
                .portfolioName(portfolio.getName())
                .ownerLabel(portfolio.getOwnerLabel())
                .tradeDate(actualTradeTime.toLocalDate())
                .tradeTime(actualTradeTime)
                .code(normalizedCode)
                .stockName(StringUtils.isNotBlank(stockName)
                        ? stockName : Objects.nonNull(stockBasic) ? stockBasic.getName() : null)
                .side(side.getCode())
                .changeType(changeType.getCode())
                .price(tradePrice)
                .priceSource(priceSource.getCode())
                .estimated(estimated ? 1 : 0)
                .quantity(changedQuantity)
                .beforeQuantity(before)
                .afterQuantity(after)
                .amount(amount)
                .source(recordSource.getCode())
                .sourceRef(StringUtils.trim(sourceRef))
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
        try {
            journalTradeMapper.insert(trade);
        } catch (DuplicateKeyException ex) {
            JournalTrade duplicate = findBySourceRef(currentUserId, normalizedCode, recordSource, sourceRef);
            if (Objects.nonNull(duplicate)) {
                return duplicate;
            }
            throw ex;
        }
        log.info("组合交易流水已记录，用户编号={}，组合编号={}，证券代码={}，方向={}，数量={}，来源={}，是否估算={}",
                currentUserId, portfolio.getId(), normalizedCode, side.getCode(), changedQuantity,
                recordSource.getCode(), estimated);
        return trade;
    }

    /**
     * 分页查询当前用户交易记录。
     *
     * @param portfolioId 组合ID
     * @param code        证券代码
     * @param side        交易方向
     * @param source      记录来源
     * @param page        页码
     * @param size        每页条数
     * @return 分页记录
     */
    @Override
    public PageResponse<TradeRecordResp> page(Long portfolioId, String code, String side, String source,
                                              Integer page, Integer size) {
        long currentPage = Objects.nonNull(page) ? Math.max(1, page) : 1;
        long pageSize = Objects.nonNull(size) ? Math.max(1, Math.min(size, 100)) : 20;
        var query = Wrappers.<JournalTrade>lambdaQuery()
                .eq(JournalTrade::getUserId, userContext.currentUserId());
        if (Objects.nonNull(portfolioId)) {
            query.eq(JournalTrade::getPortfolioId, portfolioId);
        }
        if (StringUtils.isNotBlank(code)) {
            String normalizedCode = MarketCodeUtils.normalizeHoldingCode(code);
            if (StringUtils.isBlank(normalizedCode)) {
                throw new BusinessException("证券代码无效");
            }
            query.eq(JournalTrade::getCode, normalizedCode);
        }
        if (StringUtils.isNotBlank(side)) {
            query.eq(JournalTrade::getSide, side.trim().toUpperCase());
        }
        if (StringUtils.isNotBlank(source)) {
            query.eq(JournalTrade::getSource, source.trim().toUpperCase());
        }
        query.orderByDesc(JournalTrade::getTradeDate)
                .orderByDesc(JournalTrade::getTradeTime)
                .orderByDesc(JournalTrade::getId);
        IPage<JournalTrade> tradePage = journalTradeMapper.selectPage(new Page<>(currentPage, pageSize), query);
        PageResponse<TradeRecordResp> response = new PageResponse<>();
        response.setCurrent(tradePage.getCurrent());
        response.setSize(tradePage.getSize());
        response.setTotal(tradePage.getTotal());
        response.setRecords(toResponses(tradePage.getRecords()));
        return response;
    }

    /**
     * 查询当前用户指定证券的 K 线交易标记。
     *
     * @param code 证券代码
     * @return 交易标记
     */
    @Override
    public List<TradeRecordResp> listMarkers(String code) {
        String normalizedCode = MarketCodeUtils.normalizeHoldingCode(code);
        if (StringUtils.isBlank(normalizedCode)) {
            throw new BusinessException("证券代码无效");
        }
        List<JournalTrade> latestTrades = journalTradeMapper.selectList(Wrappers.<JournalTrade>lambdaQuery()
                .eq(JournalTrade::getUserId, userContext.currentUserId())
                .eq(JournalTrade::getCode, normalizedCode)
                .orderByDesc(JournalTrade::getTradeDate)
                .orderByDesc(JournalTrade::getTradeTime)
                .orderByDesc(JournalTrade::getId)
                .last("LIMIT 500"));
        List<JournalTrade> trades = new ArrayList<>(latestTrades);
        trades.sort(Comparator
                .comparing(JournalTrade::getTradeDate,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(JournalTrade::getTradeTime,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(JournalTrade::getId,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        return toResponses(trades);
    }

    private JournalTrade findBySourceRef(Long userId, String code, PortfolioTradeSourceEnum source,
                                         String sourceRef) {
        if (StringUtils.isBlank(sourceRef)) {
            return null;
        }
        return journalTradeMapper.selectOne(Wrappers.<JournalTrade>lambdaQuery()
                .eq(JournalTrade::getUserId, userId)
                .eq(JournalTrade::getSource, source.getCode())
                .eq(JournalTrade::getSourceRef, sourceRef.trim())
                .eq(JournalTrade::getCode, code)
                .last("LIMIT 1"));
    }

    private BigDecimal positivePrice(BigDecimal price) {
        return Objects.nonNull(price) && price.signum() > 0 ? price : null;
    }

    private List<TradeRecordResp> toResponses(List<JournalTrade> trades) {
        if (CollUtil.isEmpty(trades)) {
            return new ArrayList<>();
        }
        Set<String> codes = new LinkedHashSet<>();
        LocalDate earliestSellDate = null;
        for (JournalTrade trade : trades) {
            if (StringUtils.isNotBlank(trade.getCode())) {
                codes.add(trade.getCode());
            }
            if (PortfolioTradeSideEnum.SELL.getCode().equals(trade.getSide())
                    && Objects.nonNull(trade.getTradeDate())) {
                if (Objects.isNull(earliestSellDate) || trade.getTradeDate().isBefore(earliestSellDate)) {
                    earliestSellDate = trade.getTradeDate();
                }
            }
        }
        Map<String, StockBasic> basics = new HashMap<>();
        if (CollUtil.isNotEmpty(codes)) {
            List<StockBasic> stockBasics = stockBasicMapper.selectList(Wrappers.<StockBasic>lambdaQuery()
                    .in(StockBasic::getCode, codes));
            for (StockBasic stockBasic : stockBasics) {
                basics.put(stockBasic.getCode(), stockBasic);
            }
        }
        Map<String, List<BarDaily>> barsByCode = new HashMap<>();
        if (Objects.nonNull(earliestSellDate) && CollUtil.isNotEmpty(codes)) {
            List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                    .in(BarDaily::getCode, codes)
                    .gt(BarDaily::getTradeDate, earliestSellDate)
                    .orderByAsc(BarDaily::getTradeDate));
            for (BarDaily bar : bars) {
                barsByCode.computeIfAbsent(bar.getCode(), ignored -> new ArrayList<>()).add(bar);
            }
        }
        List<TradeRecordResp> responses = new ArrayList<>();
        for (JournalTrade trade : trades) {
            StockBasic basic = basics.get(trade.getCode());
            TradeRecordResp response = TradeRecordResp.builder()
                    .id(trade.getId())
                    .tradeDate(trade.getTradeDate())
                    .tradeTime(trade.getTradeTime())
                    .code(trade.getCode())
                    .stockName(StringUtils.isNotBlank(trade.getStockName())
                            ? trade.getStockName() : Objects.nonNull(basic) ? basic.getName() : null)
                    .market(Objects.nonNull(basic) ? basic.getMarket() : null)
                    .side(trade.getSide())
                    .changeType(StringUtils.isNotBlank(trade.getChangeType())
                            ? trade.getChangeType() : PortfolioTradeChangeTypeEnum.MANUAL.getCode())
                    .quantity(trade.getQuantity())
                    .beforeQuantity(trade.getBeforeQuantity())
                    .afterQuantity(trade.getAfterQuantity())
                    .price(trade.getPrice())
                    .amount(trade.getAmount())
                    .portfolioId(trade.getPortfolioId())
                    .portfolioName(trade.getPortfolioName())
                    .ownerLabel(trade.getOwnerLabel())
                    .source(StringUtils.isNotBlank(trade.getSource())
                            ? trade.getSource() : PortfolioTradeSourceEnum.MANUAL.getCode())
                    .priceSource(trade.getPriceSource())
                    .estimated(Objects.equals(trade.getEstimated(), 1))
                    .note(trade.getNote())
                    .build();
            fillSellPerformance(response, basic, barsByCode.get(trade.getCode()));
            responses.add(response);
        }
        return responses;
    }

    private void fillSellPerformance(TradeRecordResp response, StockBasic basic, List<BarDaily> bars) {
        if (!PortfolioTradeSideEnum.SELL.getCode().equals(response.getSide())
                || Objects.isNull(response.getPrice()) || response.getPrice().signum() <= 0) {
            return;
        }
        BigDecimal latestPrice = Objects.nonNull(basic) ? positivePrice(basic.getLatestPrice()) : null;
        BigDecimal highest = null;
        BigDecimal lowest = null;
        if (CollUtil.isNotEmpty(bars)) {
            for (BarDaily bar : bars) {
                if (Objects.isNull(bar.getTradeDate()) || Objects.isNull(response.getTradeDate())
                        || !bar.getTradeDate().isAfter(response.getTradeDate())) {
                    continue;
                }
                if (Objects.nonNull(bar.getHighPrice())) {
                    highest = Objects.isNull(highest) ? bar.getHighPrice() : highest.max(bar.getHighPrice());
                }
                if (Objects.nonNull(bar.getLowPrice())) {
                    lowest = Objects.isNull(lowest) ? bar.getLowPrice() : lowest.min(bar.getLowPrice());
                }
                if (Objects.isNull(latestPrice) && Objects.nonNull(bar.getClosePrice())) {
                    latestPrice = bar.getClosePrice();
                }
            }
        }
        response.setLatestPrice(latestPrice);
        response.setLatestReturnPct(returnPct(latestPrice, response.getPrice()));
        response.setMaxRisePct(returnPct(highest, response.getPrice()));
        response.setMaxFallPct(returnPct(lowest, response.getPrice()));
    }

    private BigDecimal returnPct(BigDecimal value, BigDecimal base) {
        if (Objects.isNull(value) || Objects.isNull(base) || base.signum() <= 0) {
            return null;
        }
        return value.subtract(base)
                .multiply(BigDecimal.valueOf(100))
                .divide(base, 2, RoundingMode.HALF_UP);
    }
}
