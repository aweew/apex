package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 估值筛选列表项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValuationScreenItemResp {

    private String code;
    private String name;
    private String industry;
    private BigDecimal latestPrice;
    private BigDecimal peTtm;
    private BigDecimal pb;
    private BigDecimal score;
    private String level;
    private String levelLabel;
    private BigDecimal pePercentile;
    private BigDecimal peg;
    private BigDecimal marginOfSafety;
    private String summary;
}
