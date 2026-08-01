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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 回测成交
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("backtest_trade")
public class BacktestTrade implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 回测任务ID
     */
    private Long jobId;

    /**
     * 成交日
     */
    private LocalDate tradeDate;

    /**
     * BUY/SELL
     */
    private String side;

    /**
     * 成交价
     */
    private BigDecimal price;

    /**
     * 数量
     */
    private Integer quantity;

    /**
     * 成交额
     */
    private BigDecimal amount;

    /**
     * 费用
     */
    private BigDecimal fee;

    /**
     * 原因
     */
    private String reason;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除
     */
    @TableLogic
    private Integer deleted;
}
