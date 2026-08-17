package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.JournalCreateReq;
import com.awe.apex.quant.domain.entity.DailyAction;
import com.awe.apex.quant.domain.entity.JournalTrade;
import com.awe.apex.quant.domain.enums.PortfolioTradeChangeTypeEnum;
import com.awe.apex.quant.domain.enums.PortfolioTradePriceSourceEnum;
import com.awe.apex.quant.domain.enums.PortfolioTradeSourceEnum;
import com.awe.apex.quant.mapper.DailyActionMapper;
import com.awe.apex.quant.mapper.JournalTradeMapper;
import com.awe.apex.quant.market.MarketCodeUtils;
import com.awe.apex.quant.service.IJournalService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * 人工成交日记实现
 */
@Service
public class JournalServiceImpl implements IJournalService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Resource
    private JournalTradeMapper journalTradeMapper;

    @Resource
    private DailyActionMapper dailyActionMapper;

    @Resource
    private ApexUserContext userContext;

    /**
     * 创建当前用户的人工成交记录。
     *
     * @param req 人工成交请求
     * @return 成交记录
     */
    @Override
    public JournalTrade create(JournalCreateReq req) {
        if (Objects.nonNull(req.getRelatedActionId())) {
            requireAction(req.getRelatedActionId());
        }
        LocalDateTime now = LocalDateTime.now();
        BigDecimal amount = req.getPrice().multiply(BigDecimal.valueOf(req.getQuantity())).setScale(2, RoundingMode.HALF_UP);
        PortfolioTradeSourceEnum source = Objects.nonNull(req.getRelatedActionId())
                ? PortfolioTradeSourceEnum.DAILY_ACTION : PortfolioTradeSourceEnum.MANUAL;
        JournalTrade trade = JournalTrade.builder()
                .userId(userContext.currentUserId())
                .tradeDate(LocalDate.parse(req.getTradeDate(), DAY))
                .code(MarketCodeUtils.normalizeCode(req.getCode()))
                .side(req.getSide().toUpperCase())
                .changeType(PortfolioTradeChangeTypeEnum.MANUAL.getCode())
                .price(req.getPrice())
                .priceSource(PortfolioTradePriceSourceEnum.USER_REPORTED.getCode())
                .estimated(0)
                .quantity(req.getQuantity())
                .amount(amount)
                .relatedActionId(req.getRelatedActionId())
                .source(source.getCode())
                .note(req.getNote())
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
        journalTradeMapper.insert(trade);
        return trade;
    }

    /**
     * 查询当前用户最近的人工成交记录。
     *
     * @param limit 返回条数
     * @return 成交记录
     */
    @Override
    public List<JournalTrade> latest(int limit) {
        return journalTradeMapper.selectList(Wrappers.<JournalTrade>lambdaQuery()
                .eq(JournalTrade::getUserId, userContext.currentUserId())
                .orderByDesc(JournalTrade::getId)
                .last("limit " + Math.max(1, Math.min(limit, 200))));
    }

    /**
     * 根据日终清单创建当前用户的成交记录。
     *
     * @param actionId 日终清单ID
     * @param price    成交价
     * @param quantity 成交数量
     * @return 成交记录
     */
    @Override
    public JournalTrade fromAction(Long actionId, BigDecimal price, Integer quantity) {
        DailyAction action = requireAction(actionId);
        if ("HOLD".equalsIgnoreCase(action.getAction())) {
            throw new BusinessException("持有项无需录入成交");
        }
        JournalCreateReq req = new JournalCreateReq();
        req.setTradeDate(action.getActionDate().toString());
        req.setCode(action.getCode());
        req.setSide("REDUCE".equalsIgnoreCase(action.getAction()) ? "SELL" : action.getAction());
        req.setPrice(price);
        req.setQuantity(quantity);
        req.setRelatedActionId(actionId);
        req.setNote("来自日终清单 " + action.getStrategyId());
        return create(req);
    }

    private DailyAction requireAction(Long actionId) {
        DailyAction action = dailyActionMapper.selectOne(Wrappers.<DailyAction>lambdaQuery()
                .eq(DailyAction::getId, actionId)
                .eq(DailyAction::getUserId, userContext.currentUserId())
                .last("LIMIT 1"));
        if (Objects.isNull(action)) {
            throw new BusinessException("清单不存在");
        }
        return action;
    }
}
