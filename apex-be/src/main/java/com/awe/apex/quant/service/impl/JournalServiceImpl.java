package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.domain.dto.JournalCreateReq;
import com.awe.apex.quant.domain.entity.DailyAction;
import com.awe.apex.quant.domain.entity.JournalTrade;
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

    @Override
    public JournalTrade create(JournalCreateReq req) {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal amount = req.getPrice().multiply(BigDecimal.valueOf(req.getQuantity())).setScale(2, RoundingMode.HALF_UP);
        JournalTrade trade = JournalTrade.builder()
                .tradeDate(LocalDate.parse(req.getTradeDate(), DAY))
                .code(MarketCodeUtils.normalizeCode(req.getCode()))
                .side(req.getSide().toUpperCase())
                .price(req.getPrice())
                .quantity(req.getQuantity())
                .amount(amount)
                .relatedActionId(req.getRelatedActionId())
                .note(req.getNote())
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
        journalTradeMapper.insert(trade);
        return trade;
    }

    @Override
    public List<JournalTrade> latest(int limit) {
        return journalTradeMapper.selectList(Wrappers.<JournalTrade>lambdaQuery()
                .orderByDesc(JournalTrade::getId)
                .last("limit " + Math.max(1, Math.min(limit, 200))));
    }

    @Override
    public JournalTrade fromAction(Long actionId, BigDecimal price, Integer quantity) {
        DailyAction action = dailyActionMapper.selectById(actionId);
        if (Objects.isNull(action)) {
            throw new BusinessException("清单不存在");
        }
        if ("HOLD".equalsIgnoreCase(action.getAction())) {
            throw new BusinessException("持有项无需录入成交");
        }
        JournalCreateReq req = new JournalCreateReq();
        req.setTradeDate(action.getActionDate().toString());
        req.setCode(action.getCode());
        req.setSide(action.getAction());
        req.setPrice(price);
        req.setQuantity(quantity);
        req.setRelatedActionId(actionId);
        req.setNote("来自日终清单 " + action.getStrategyId());
        return create(req);
    }
}
