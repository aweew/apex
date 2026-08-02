package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 观察池保存请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObservePoolSaveReq {

    /**
     * 主键（更新时传）
     */
    private Long id;

    /**
     * 证券代码
     */
    private String code;

    /**
     * 证券简称
     */
    private String name;

    /**
     * 市场
     */
    private String market;

    /**
     * 方向 BUY/SELL
     */
    private String side;

    /**
     * 关注原因
     */
    private String reason;

    /**
     * 详细操作指导
     */
    private String guideText;

    /**
     * 触发类型
     */
    private String triggerType;

    /**
     * 补充触发条件
     */
    private String triggerExpr;

    /**
     * 触发价
     */
    private BigDecimal triggerPrice;

    /**
     * 止损价
     */
    private BigDecimal stopLoss;

    /**
     * 目标价
     */
    private BigDecimal targetPrice;

    /**
     * 基准价
     */
    private BigDecimal basePrice;

    /**
     * 优先级 1-5
     */
    private Integer priority;

    /**
     * 状态
     */
    private String status;

    /**
     * 备注
     */
    private String note;

    /**
     * 标签
     */
    private String tags;
}
