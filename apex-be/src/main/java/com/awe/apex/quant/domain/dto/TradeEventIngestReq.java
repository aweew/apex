package com.awe.apex.quant.domain.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** AI 交易解析入库请求。 */
@Data
public class TradeEventIngestReq {
    /** 交易者名称 */ private String traderName;
    /** 稳定微信身份 */ private String wechatPeerId;
    /** TRADE / TRADE_INTENT / POSITION / CASH / IGNORE */ private String eventType;
    /** BUY / SELL / ADD / REDUCE / CLEAR */ private String side;
    /** 证券代码 */ private String symbol;
    /** 证券名称 */ private String stockName;
    /** 数量 */ private Integer quantity;
    /** 成交价格 */ private BigDecimal price;
    /** 成交时间 */ private LocalDateTime tradeTime;
    /** 置信度 */ private BigDecimal confidence;
    /** WECHAT_TEXT / WECHAT_IMAGE / MANUAL / OTHER */ private String source;
    /** 原文 */ private String rawText;
    /** 图片地址 */ private String imageUrl;
    /** 调用方幂等键 */ private String idempotencyKey;
}
