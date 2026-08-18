package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 今日智能决策汇总
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionTodayResp {

    /** 决策运行号 */
    private String runNo;

    /** LIVE/REPLAY/SHADOW */
    private String runMode;

    /** 本次决策可见数据截止时间 */
    private LocalDateTime asOfTime;

    /**
     * 市场行情实际截至日。
     */
    private LocalDate dataAsOf;

    /**
     * 当日决策是否已生成。
     */
    private Boolean generated;

    /** 规则版本 */
    private String ruleVersion;

    /** 模型版本 */
    private String modelVersion;

    /** 特征版本 */
    private String featureVersion;

    /**
     * 决策日
     */
    private LocalDate actionDate;

    /**
     * 自选分组
     */
    private String groupName;

    /**
     * 股票池数量
     */
    private Integer universeCount;

    /**
     * 买入建议
     */
    private List<DecisionItemResp> buys;

    /**
     * 卖出建议
     */
    private List<DecisionItemResp> sells;

    /**
     * 继续持有
     */
    private List<DecisionItemResp> holds;

    /**
     * 全部条目（按动作优先级）
     */
    private List<DecisionItemResp> items;

    /**
     * 买入条数
     */
    private Integer buyCount;

    /**
     * 卖出条数
     */
    private Integer sellCount;

    /**
     * 持有条数
     */
    private Integer holdCount;

    /**
     * 可执行提示条数（executableHint=true）
     */
    private Integer executableCount;

    /**
     * 主线匹配条数
     */
    private Integer mainlineMatchCount;

    /**
     * 低估/偏低条数
     */
    private Integer valuationCheapCount;

    /**
     * 合理估值条数
     */
    private Integer valuationFairCount;

    /**
     * 高估/偏高条数
     */
    private Integer valuationRichCount;

    /**
     * 风控摘要
     */
    private String riskNote;

    /**
     * 每日市场简报（大盘/风格/量能/情绪提示）
     */
    private MarketBriefingResp marketBriefing;

    /**
     * 说明
     */
    private String message;

    /**
     * 同步观察池：新建数
     */
    private Integer observeCreated;

    /**
     * 同步观察池：更新数
     */
    private Integer observeUpdated;

    /**
     * 同步观察池：合计写入
     */
    private Integer observeUpserted;
}
