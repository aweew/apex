package com.awe.apex.quant.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 解析后的交易事件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("trade_event")
public class TradeEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 交易者ID */
    private Long traderId;
    /** TRADE / TRADE_INTENT / POSITION / CASH / IGNORE */
    private String eventType;
    /** 证券代码 */
    private String symbol;
    /** 证券简称 */
    private String stockName;
    /** BUY / SELL / ADD / REDUCE / CLEAR */
    private String side;
    /** 数量 */
    private Integer quantity;
    /** 成交价格 */
    private BigDecimal price;
    /** 成交时间 */
    private LocalDateTime tradeTime;
    /** 解析置信度 */
    private BigDecimal confidence;
    /** WECHAT_TEXT / WECHAT_IMAGE / MANUAL / OTHER */
    private String source;
    /** 原始文本 */
    private String rawText;
    /** 调用方幂等键 */
    private String idempotencyKey;
    /** PENDING_CONFIRM / CONFIRMED / REJECTED / IMPORTED */
    private String status;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;
}
