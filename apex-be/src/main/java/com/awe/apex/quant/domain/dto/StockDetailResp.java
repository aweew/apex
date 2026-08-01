package com.awe.apex.quant.domain.dto;

import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.StockBasic;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 股票详情（基本信息 + K 线）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockDetailResp {

    /**
     * 基本信息/快照
     */
    private StockBasic basic;

    /**
     * 日线 K 线（升序）
     */
    private List<BarDaily> bars;

    /**
     * 相对沪深300：20日超额（百分点）
     */
    private BigDecimal rs20VsHs300;

    /**
     * 相对沪深300：60日超额（百分点）
     */
    private BigDecimal rs60VsHs300;

    /**
     * 量比（当日量 / 近20日均量）
     */
    private BigDecimal volumeRatio;

    /**
     * 数据说明
     */
    private String note;
}
