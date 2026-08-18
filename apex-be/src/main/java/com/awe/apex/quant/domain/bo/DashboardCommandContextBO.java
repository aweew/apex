package com.awe.apex.quant.domain.bo;

import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.dto.MorningBriefingResp;
import com.awe.apex.quant.domain.dto.ObservePoolResp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 盘前指挥规则计算上下文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardCommandContextBO {

    /**
     * 规则计算时间，测试可固定；为空时使用系统当前时间。
     */
    private LocalDateTime currentTime;

    /**
     * A 股市场简报。
     */
    private MarketBriefingResp marketBriefing;

    /**
     * 全局盘前晨报。
     */
    private MorningBriefingResp morningBriefing;

    /**
     * 当前用户的目标交易日决策。
     */
    private DecisionTodayResp decision;

    /**
     * 当前用户的观察提醒。
     */
    private List<ObservePoolResp> observeAlerts;

    /**
     * 当前用户的智能决策任务是否正在运行。
     */
    private Boolean decisionRunning;
}
