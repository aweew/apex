package com.awe.apex.quant.domain.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Bot 识别出的单笔持仓成交。
 */
@Data
public class BotTradeInput {

    /**
     * 证券代码，可由证券简称唯一匹配后回填。
     */
    private String code;

    /**
     * 证券简称。
     */
    private String name;

    /**
     * 本次成交数量。
     */
    private Integer quantity;

    /**
     * 本次实际成交价。
     */
    private BigDecimal tradePrice;

    /**
     * 本次实际成交时间，为空时使用当前时间。
     */
    private LocalDateTime tradeTime;
}
