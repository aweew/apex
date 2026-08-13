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
 * 已确认的正式交易流水。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("trader_trade")
public class Trade implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 交易者ID */
    private Long traderId;
    /** 证券代码 */
    private String symbol;
    /** 证券简称 */
    private String stockName;
    /** BUY / SELL */
    private String side;
    /** 成交数量 */
    private Integer quantity;
    /** 成交价格 */
    private BigDecimal price;
    /** 成交金额 */
    private BigDecimal amount;
    /** 成交时间 */
    private LocalDateTime tradeTime;
    /** 证据ID */
    private Long evidenceId;
    /** VALID / CANCELLED */
    private String status;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;
}
