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
 * 观察池
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("observe_pool")
public class ObservePool implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
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
     * 方向 BUY=买入观察 / SELL=卖出 / MOOD=情绪风向标（非买卖）
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
     * 触发时间
     */
    private LocalDateTime triggeredAt;

    /**
     * 备注
     */
    private String note;

    /**
     * 标签
     */
    private String tags;

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
