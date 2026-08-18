package com.awe.apex.quant.domain.dto;

import com.awe.apex.quant.domain.entity.PortfolioHolding;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 组合列表项 / 详情摘要
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioSummaryResp {

    /**
     * 组合ID
     */
    private Long id;

    /**
     * 名称
     */
    private String name;

    /**
     * 备注
     */
    private String note;

    /**
     * 归属人
     */
    private String ownerLabel;

    /**
     * 是否默认
     */
    private Boolean isDefault;

    /**
     * 当前用户是否可编辑
     */
    private Boolean editable;

    /**
     * 状态
     */
    private String status;

    /**
     * 排序
     */
    private Integer sortNo;

    /**
     * 持仓只数
     */
    private Integer positionCount;

    /**
     * 总市值
     */
    private BigDecimal marketValue;

    /**
     * 现金余额
     */
    private BigDecimal cashBalance;

    /**
     * 总权益（持仓市值 + 现金余额）
     */
    private BigDecimal totalEquity;

    /**
     * 成本市值
     */
    private BigDecimal costValue;

    /**
     * 累计浮盈
     */
    private BigDecimal totalPnl;

    /**
     * 今日浮盈
     */
    private BigDecimal todayPnl;

    /**
     * 今日涨跌幅%
     */
    private BigDecimal todayPct;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 最早有效行情时间
     */
    private LocalDateTime quoteTime;

    /**
     * 缺少行情的持仓数量
     */
    private Integer missingQuoteCount;

    /**
     * 组合级研判简报（详情时填充）
     */
    private PortfolioBriefResp brief;

    /**
     * 前三仓位（列表轻量填充，按权重）
     */
    @Builder.Default
    private List<PortfolioTopHoldingResp> topHoldings = new ArrayList<>();

    /**
     * 持仓明细（详情时填充）
     */
    @Builder.Default
    private List<PortfolioHolding> holdings = new ArrayList<>();
}
