package com.awe.apex.quant.domain.dto;

import com.awe.apex.quant.domain.entity.BacktestEquity;
import com.awe.apex.quant.domain.entity.BacktestJob;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 组合回测响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioBacktestResp {

    /**
     * 汇总任务（code=PORTFOLIO）
     */
    private BacktestJob job;

    /**
     * 参与代码
     */
    private List<String> codes;

    /**
     * 单票结果
     */
    private List<BatchBacktestItemResp> legs;

    /**
     * 组合权益曲线
     */
    private List<BacktestEquity> equities;

    /**
     * 基准代码
     */
    private String benchmarkCode;

    /**
     * 基准买入持有归一权益
     */
    private List<EquityPointResp> benchmarkEquities;

    /**
     * 免责声明
     */
    private String disclaimer;
}
