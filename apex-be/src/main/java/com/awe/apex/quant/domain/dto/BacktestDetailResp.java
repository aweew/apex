package com.awe.apex.quant.domain.dto;

import com.awe.apex.quant.domain.entity.BacktestEquity;
import com.awe.apex.quant.domain.entity.BacktestJob;
import com.awe.apex.quant.domain.entity.BacktestTrade;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 回测详情响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestDetailResp {

    /**
     * 任务
     */
    private BacktestJob job;

    /**
     * 成交
     */
    private List<BacktestTrade> trades;

    /**
     * 资金曲线
     */
    private List<BacktestEquity> equities;

    /**
     * 每笔期望收益（由闭合交易推算）
     */
    private BigDecimal expectancy;

    /**
     * 免责声明
     */
    private String disclaimer;
}
