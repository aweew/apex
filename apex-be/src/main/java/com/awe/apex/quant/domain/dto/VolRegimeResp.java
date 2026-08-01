package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 市场波动率体制
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VolRegimeResp {

    /**
     * 基准代码
     */
    private String code;

    /**
     * 近20日年化波动
     */
    private BigDecimal realizedVol20;

    /**
     * 波动率一年分位（0~1）
     */
    private BigDecimal volPercentile;

    /**
     * 体制：LOW / MID / HIGH
     */
    private String regime;

    /**
     * 说明
     */
    private String message;
}
