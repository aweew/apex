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
 * 用户选股策略规则
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("screener_strategy_rule")
public class ScreenerStrategyRule implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 策略ID */
    private Long strategyId;

    /** 规则类型 */
    private String ruleType;

    /** 操作符 */
    private String operatorCode;

    /** 最小值或单值 */
    private BigDecimal minValue;

    /** 最大值 */
    private BigDecimal maxValue;

    /** 整数参数 */
    private Integer intValue;

    /** 文本参数 */
    private String textValue;

    /** 布尔参数 */
    private Integer boolValue;

    /** 回看交易日数 */
    private Integer lookbackDays;

    /** 容错阈值 */
    private BigDecimal toleranceValue;

    /** 排序号 */
    private Integer sortNo;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;
}
