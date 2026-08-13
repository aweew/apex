package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.JsonUtils;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.TradeEventIngestReq;
import com.awe.apex.quant.domain.dto.TradeEventIngestResp;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.Trade;
import com.awe.apex.quant.domain.entity.TradeEvidence;
import com.awe.apex.quant.domain.entity.TradeEvent;
import com.awe.apex.quant.domain.entity.Trader;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.TradeEvidenceMapper;
import com.awe.apex.quant.mapper.TradeEventMapper;
import com.awe.apex.quant.mapper.TradeMapper;
import com.awe.apex.quant.mapper.TraderMapper;
import com.awe.apex.quant.service.ITradeEventService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * AI 交易事件服务实现。
 */
@Service
public class TradeEventServiceImpl implements ITradeEventService {

    private static final BigDecimal AUTO_CONFIRM_CONFIDENCE = new BigDecimal("0.95");
    private static final BigDecimal PENDING_CONFIRM_CONFIDENCE = new BigDecimal("0.80");

    @Resource private TraderMapper traderMapper;
    @Resource private TradeEventMapper tradeEventMapper;
    @Resource private TradeEvidenceMapper tradeEvidenceMapper;
    @Resource private TradeMapper tradeMapper;
    @Resource private StockBasicMapper stockBasicMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TradeEventIngestResp ingest(TradeEventIngestReq request) {
        validateRequest(request);
        if (StringUtils.isNotBlank(request.getIdempotencyKey())) {
            TradeEvent existing = tradeEventMapper.selectOne(Wrappers.<TradeEvent>lambdaQuery()
                    .eq(TradeEvent::getIdempotencyKey, request.getIdempotencyKey()).last("LIMIT 1"));
            if (Objects.nonNull(existing)) return new TradeEventIngestResp(existing.getId(), existing.getStatus());
        }
        StockBasic stock = validateStock(request);
        Trader trader = findOrCreateTrader(request);
        String status = resolveStatus(request);
        TradeEvent event = TradeEvent.builder().traderId(trader.getId()).eventType(normalize(request.getEventType()))
                .symbol(stock == null ? null : stock.getCode()).stockName(stock == null ? request.getStockName() : stock.getName())
                .side(normalize(request.getSide())).quantity(request.getQuantity()).price(request.getPrice()).tradeTime(request.getTradeTime())
                .confidence(request.getConfidence()).source(normalize(request.getSource())).rawText(request.getRawText())
                .idempotencyKey(request.getIdempotencyKey()).status(status).createTime(LocalDateTime.now()).updateTime(LocalDateTime.now()).deleted(0).build();
        tradeEventMapper.insert(event);
        TradeEvidence evidence = TradeEvidence.builder().tradeEventId(event.getId()).traderId(trader.getId()).source(event.getSource())
                .rawText(request.getRawText()).imageUrl(request.getImageUrl()).parsedResult(JsonUtils.toJsonString(request))
                .confidence(request.getConfidence()).createTime(LocalDateTime.now()).build();
        tradeEvidenceMapper.insert(evidence);
        if ("CONFIRMED".equals(status)) createTrade(event, evidence.getId());
        return new TradeEventIngestResp(event.getId(), status);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TradeEventIngestResp confirm(Long id) {
        TradeEvent event = requireEvent(id);
        if ("CONFIRMED".equals(event.getStatus())) return new TradeEventIngestResp(event.getId(), event.getStatus());
        if (!"PENDING_CONFIRM".equals(event.getStatus())) throw new BusinessException("当前交易事件不能确认");
        ensureTradeable(event);
        event.setStatus("CONFIRMED"); event.setUpdateTime(LocalDateTime.now()); tradeEventMapper.updateById(event);
        TradeEvidence evidence = tradeEvidenceMapper.selectOne(Wrappers.<TradeEvidence>lambdaQuery()
                .eq(TradeEvidence::getTradeEventId, event.getId())
                .last("LIMIT 1"));
        if (Objects.isNull(evidence)) {
            throw new BusinessException("交易事件缺少原始证据，不能确认");
        }
        createTrade(event, evidence.getId());
        return new TradeEventIngestResp(event.getId(), event.getStatus());
    }

    /** {@inheritDoc} */
    @Override
    public TradeEventIngestResp reject(Long id) {
        TradeEvent event = requireEvent(id);
        if ("CONFIRMED".equals(event.getStatus())) throw new BusinessException("已确认交易事件不能拒绝");
        event.setStatus("REJECTED"); event.setUpdateTime(LocalDateTime.now()); tradeEventMapper.updateById(event);
        return new TradeEventIngestResp(event.getId(), event.getStatus());
    }

    private void validateRequest(TradeEventIngestReq request) {
        if (Objects.isNull(request) || StringUtils.isBlank(request.getTraderName()) || StringUtils.isBlank(request.getEventType())
                || Objects.isNull(request.getConfidence()) || request.getConfidence().compareTo(BigDecimal.ZERO) < 0 || request.getConfidence().compareTo(BigDecimal.ONE) > 0
                || StringUtils.isBlank(request.getSource()) || StringUtils.isBlank(request.getRawText())) throw new BusinessException("交易事件字段不完整或置信度无效");
    }

    private StockBasic validateStock(TradeEventIngestReq request) {
        if (!"TRADE".equals(normalize(request.getEventType()))) return null;
        if (StringUtils.isBlank(request.getSymbol())) throw new BusinessException("正式交易事件必须提供股票代码");
        StockBasic stock = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery().eq(StockBasic::getCode, request.getSymbol().trim()).last("LIMIT 1"));
        if (Objects.isNull(stock)) throw new BusinessException("股票代码不存在或已失效");
        return stock;
    }

    private Trader findOrCreateTrader(TradeEventIngestReq request) {
        Trader trader = StringUtils.isNotBlank(request.getWechatPeerId()) ? traderMapper.selectOne(Wrappers.<Trader>lambdaQuery().eq(Trader::getWechatPeerId, request.getWechatPeerId()).last("LIMIT 1")) : null;
        if (Objects.isNull(trader)) trader = traderMapper.selectOne(Wrappers.<Trader>lambdaQuery().eq(Trader::getName, request.getTraderName().trim()).last("LIMIT 1"));
        if (Objects.nonNull(trader)) return trader;
        trader = Trader.builder().name(request.getTraderName().trim()).nickname(request.getTraderName().trim()).wechatPeerId(request.getWechatPeerId()).initialCapital(new BigDecimal("1000000.00")).status("ACTIVE").createTime(LocalDateTime.now()).updateTime(LocalDateTime.now()).deleted(0).build();
        traderMapper.insert(trader); return trader;
    }

    private String resolveStatus(TradeEventIngestReq request) {
        if (!"TRADE".equals(normalize(request.getEventType())) || request.getConfidence().compareTo(PENDING_CONFIRM_CONFIDENCE) < 0) return "REJECTED";
        if (request.getConfidence().compareTo(AUTO_CONFIRM_CONFIDENCE) >= 0 && isTradeable(request)) return "CONFIRMED";
        return "PENDING_CONFIRM";
    }

    private boolean isTradeable(TradeEventIngestReq request) {
        String side = normalize(request.getSide());
        return "BUY".equals(side) || "SELL".equals(side) || "ADD".equals(side) || "REDUCE".equals(side);
    }

    private void ensureTradeable(TradeEvent event) {
        if (!isTradeable(event) || Objects.isNull(event.getQuantity()) || event.getQuantity() <= 0
                || Objects.isNull(event.getPrice()) || event.getPrice().signum() <= 0) {
            throw new BusinessException("交易事件缺少可确认的方向、数量或价格");
        }
    }

    private boolean isTradeable(TradeEvent event) {
        return "BUY".equals(event.getSide()) || "SELL".equals(event.getSide())
                || "ADD".equals(event.getSide()) || "REDUCE".equals(event.getSide());
    }

    private TradeEvent requireEvent(Long id) {
        TradeEvent event = tradeEventMapper.selectById(id);
        if (Objects.isNull(event)) {
            throw new BusinessException("交易事件不存在");
        }
        return event;
    }

    private String normalize(String value) {
        return StringUtils.isBlank(value) ? null : value.trim().toUpperCase();
    }

    private void createTrade(TradeEvent event, Long evidenceId) {
        ensureTradeable(event);
        Trade existing = tradeMapper.selectOne(Wrappers.<Trade>lambdaQuery()
                .eq(Trade::getEvidenceId, evidenceId)
                .last("LIMIT 1"));
        if (Objects.nonNull(existing)) {
            return;
        }
        BigDecimal amount = event.getPrice().multiply(BigDecimal.valueOf(event.getQuantity())).setScale(2, RoundingMode.HALF_UP);
        String tradeSide = "ADD".equals(event.getSide()) ? "BUY" : "REDUCE".equals(event.getSide()) ? "SELL" : event.getSide();
        Trade trade = Trade.builder().traderId(event.getTraderId()).symbol(event.getSymbol()).stockName(event.getStockName())
                .side(tradeSide).quantity(event.getQuantity()).price(event.getPrice()).amount(amount).tradeTime(event.getTradeTime())
                .evidenceId(evidenceId).status("VALID").createTime(LocalDateTime.now()).updateTime(LocalDateTime.now()).deleted(0).build();
        tradeMapper.insert(trade);
    }
}
