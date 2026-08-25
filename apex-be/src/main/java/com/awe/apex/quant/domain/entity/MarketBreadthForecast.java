package com.awe.apex.quant.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 盘前涨跌比预测及收盘回测快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("market_breadth_forecast")
public class MarketBreadthForecast {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 预测对应交易日。 */
    private LocalDate tradeDate;

    /** 预测生成时间。 */
    private LocalDateTime generatedAt;

    /** 规则模型版本。 */
    private String modelVersion;

    /** 盘前输入数据截至时间。 */
    private LocalDateTime sourceAsOf;

    /** 预测上涨占比，平盘剔除后计算，单位百分比。 */
    private BigDecimal predictedUpRatio;

    /** 预测下跌占比，平盘剔除后计算，单位百分比。 */
    private BigDecimal predictedDownRatio;

    /** 已结算历史样本带来的校准值，单位百分点。 */
    private BigDecimal calibrationAdjustment;

    /** 预测置信度，高/中/低。 */
    private String confidence;

    /** 本次盘前依据摘要。 */
    private String factorSummary;

    /** 实际上涨家数。 */
    private Integer actualUpCount;

    /** 实际下跌家数。 */
    private Integer actualDownCount;

    /** 实际上涨占比，平盘剔除后计算，单位百分比。 */
    private BigDecimal actualUpRatio;

    /** 实际下跌占比，平盘剔除后计算，单位百分比。 */
    private BigDecimal actualDownRatio;

    /** 预测与实际上涨占比的绝对误差，单位百分点。 */
    private BigDecimal absoluteError;

    /** 涨跌方向是否命中。 */
    private Boolean directionHit;

    /** 收盘回测结论。 */
    private String analysisSummary;

    /** 收盘回测结算时间。 */
    private LocalDateTime settledAt;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新时间。 */
    private LocalDateTime updateTime;

    /** 逻辑删除标识。 */
    @TableLogic
    private Integer deleted;
}
