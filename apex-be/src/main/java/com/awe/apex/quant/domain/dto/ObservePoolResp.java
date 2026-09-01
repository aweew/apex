package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 观察池列表项（含评估字段）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObservePoolResp {

    /**
     * 主键
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
     * 方向 BUY/SELL/MOOD
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
     * 优先级
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
     * 最近一次智能决策写入时间
     */
    private LocalDateTime decisionUpdatedAt;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 最新价
     */
    private BigDecimal latestPrice;

    /**
     * 当日涨跌幅%
     */
    private BigDecimal pctChg;

    /**
     * 距触发价%（正=还需上涨到达，负=已越过）
     */
    private BigDecimal pctToTrigger;

    /**
     * 距止损价%
     */
    private BigDecimal pctToStop;

    /**
     * 距目标价%
     */
    private BigDecimal pctToTarget;

    /**
     * 状态提示文案
     */
    private String statusHint;

    /**
     * 触发条件可读文案
     */
    private String triggerLabel;

    /**
     * 技术指标监控清单
     */
    private List<ObserveTechSignal> techSignals;

    /**
     * 技术有利项数量
     */
    private Integer techHitCount;

    /**
     * 技术监控总项数
     */
    private Integer techTotal;

    /**
     * 技术共振摘要
     */
    private String techSummary;

    /**
     * 策略 ID（S1/S2/S3/MOOD…）
     */
    private String strategyId;

    /**
     * 策略名称
     */
    private String strategyName;

    /**
     * 观察类型：回调买入 / 突破观察 / 趋势观察 / 情绪温度计
     */
    private String setupStyle;

    /**
     * 结构化选股/观察理由（多条）
     */
    private List<String> pickReasons;

    /**
     * 风险提示（如近端涨幅过大勿追）
     */
    private List<String> riskFlags;

    /**
     * 近 2 日涨跌幅%
     */
    private BigDecimal pct2d;

    /**
     * 近 5 日涨跌幅%
     */
    private BigDecimal pct5d;

    /**
     * 估值档位
     */
    private String valuationLevel;

    /**
     * 估值中文结论
     */
    private String valuationLabel;

    /**
     * 估值综合分
     */
    private BigDecimal valuationScore;

    /** 最近 20 个交易日收盘价，用于列表缩略图。 */
    private List<BigDecimal> sparkCloses;

    /**
     * 估值一句话
     */
    private String valuationSummary;
}
