package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 选股页 · 市场与股票池规模
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenerMetaResp {

    /**
     * 全市场股票数（stock_basic）
     */
    private Integer marketCount;

    /**
     * 策略股票池数（最新批次 universe）
     */
    private Integer universeCount;

    /**
     * 股票池批次号
     */
    private String universeBatchNo;

    /**
     * 一句话说明
     */
    private String note;
}
