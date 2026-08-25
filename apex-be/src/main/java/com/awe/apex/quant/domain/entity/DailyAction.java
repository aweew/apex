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
@TableName("daily_action")
public class DailyAction implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属用户ID */ private Long userId;
    /** 清单日期 */ private LocalDate actionDate;
    /** 决策运行ID */ private Long runId;
    /** 运行内排序 */ private Integer rankNo;
    /** 证券代码 */ private String code;
    /** 简称 */ private String name;
    /** BUY/REDUCE/SELL/HOLD */ private String action;
    /** 策略ID */ private String strategyId;
    /** 理由 */ private String reason;
    /** 建议仓位 */ private BigDecimal suggestedWeight;
    /** 决策时参考价 */ private BigDecimal referencePrice;
    /** 决策止损价 */ private BigDecimal stopLossPrice;
    /** 决策止盈价 */ private BigDecimal takeProfitPrice;
    /** 离场条件 */ private String exitRule;
    /** 综合评分 */ private BigDecimal score;
    /** 共振策略数 */ private Integer confluenceCount;
    /** 基本面要点 */ private String fundNote;
    /** 关联策略信号ID */ private Long signalId;
    /** 是否匹配当日主线 */ private Integer mainlineMatch;
    /** 匹配主线名称 */ private String mainlineName;
    /** 评分/仓位拆解 */ private String scoreExplain;
    /** 共振策略ID列表 CSV */ private String strategiesCsv;
    /** 估值档位 */ private String valuationLevel;
    /** 估值中文标签 */ private String valuationLabel;
    /** 估值综合分 */ private BigDecimal valuationScore;
    /** 估值一句话 */ private String valuationSummary;
    /** 估值×策略联动提示 */ private String linkHint;
    /** 决策通道CORE核心防守线或GROWTH科技成长线 */ private String decisionLane;
    /** 科技候选未进入成长线的原因 */ private String growthLaneRejectReason;
    /** 风险旗标 CSV */ private String riskFlags;
    /** 是否可执行提示 1/0 */ private Integer executableHint;
    /** 决策置信度 */ private BigDecimal confidence;
    /** 决策不确定性 */ private BigDecimal uncertainty;
    /** 决策状态 */ private String decisionStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic private Integer deleted;
}
