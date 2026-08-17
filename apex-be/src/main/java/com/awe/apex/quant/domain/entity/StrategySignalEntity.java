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
 * 策略信号实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("strategy_signal")
public class StrategySignalEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 证券代码
     */
    private String code;

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
