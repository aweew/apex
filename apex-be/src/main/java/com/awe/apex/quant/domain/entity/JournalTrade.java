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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("journal_trade")
public class JournalTrade implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID。
     */
    private Long userId;

    /**
     * 组合ID。
     */
    private Long portfolioId;

    /**
     * 交易发生时的组合名称。
     */
    private String portfolioName;

    /**
     * 交易发生时的组合归属人标签。
     */
    private String ownerLabel;

    /**
     * 成交日。
     */
    private LocalDate tradeDate;

    /**
     * 成交或持仓变动时间。
     */
    private LocalDateTime tradeTime;

    /**
     * 证券代码。
     */
    private String code;

    /**
     * 证券简称。
     */
    private String stockName;

    /**
     * 交易方向 BUY/SELL。
     */
    private String side;

    /**
     * 持仓变动类型。
     */
    private String changeType;

    /**
     * 成交价或估算参考价。
     */
    private BigDecimal price;

    /**
     * 价格来源。
     */
    private String priceSource;

    /**
     * 价格是否为估算值，0否1是。
     */
    private Integer estimated;

    /**
     * 本次变化数量。
     */
    private Integer quantity;

    /**
     * 变动前持仓数量。
     */
    private Integer beforeQuantity;

    /**
     * 变动后持仓数量。
     */
    private Integer afterQuantity;

    /**
     * 成交额或估算金额。
     */
    private BigDecimal amount;

    /**
     * 关联日终清单ID。
     */
    private Long relatedActionId;

    /**
     * 记录来源。
     */
    private String source;

    /**
     * 来源请求或业务引用。
     */
    private String sourceRef;

    /**
     * 备注。
     */
    private String note;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记。
     */
    @TableLogic
    private Integer deleted;
}
