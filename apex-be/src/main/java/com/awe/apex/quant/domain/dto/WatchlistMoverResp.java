package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 自选异动提醒
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistMoverResp {

    /**
     * 涨跌阈值（%）
     */
    private BigDecimal threshold;

    /**
     * 大涨列表
     */
    private List<WatchlistResp> gainers;

    /**
     * 大跌列表
     */
    private List<WatchlistResp> losers;

    /**
     * 提示文案
     */
    private String message;
}
