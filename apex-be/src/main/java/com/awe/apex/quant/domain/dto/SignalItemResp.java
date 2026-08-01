package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 策略信号（含证券名称）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalItemResp {

    /**
     * 主键
     */
    private Long id;

    /**
     * 证券代码
     */
    private String code;

    /**
     * 证券名称
     */
    private String name;

    /**
     * 策略ID
     */
    private String strategyId;

    /**
     * 信号日
     */
    private LocalDate signalDate;

    /**
     * BUY/SELL/HOLD
     */
    private String side;

    /**
     * 评分
     */
    private BigDecimal score;

    /**
     * 理由JSON
     */
    private String reasonJson;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
