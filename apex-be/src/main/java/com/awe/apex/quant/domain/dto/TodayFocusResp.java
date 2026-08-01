package com.awe.apex.quant.domain.dto;

import com.awe.apex.quant.domain.entity.StrategySignalEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 今日关注清单
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TodayFocusResp {

    /**
     * 异动
     */
    private WatchlistMoverResp movers;

    /**
     * 高分 BUY
     */
    private List<StrategySignalEntity> buySignals;

    /**
     * 高分 SELL
     */
    private List<StrategySignalEntity> sellSignals;

    /**
     * 风控告警摘要
     */
    private List<String> riskAlerts;

    /**
     * 市场宽度摘要
     */
    private String breadthMessage;

    /**
     * 多策略共振
     */
    private List<SignalConfluenceItem> confluence;

    /**
     * 说明
     */
    private String message;
}
