package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 轻量估值摘要（决策/观察池用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValuationBriefResp {

    private String code;
    private String level;
    private String levelLabel;
    private BigDecimal score;
    private BigDecimal peTtm;
    private BigDecimal pb;
    private BigDecimal pePercentile;
    private BigDecimal peg;
    private BigDecimal marginOfSafety;
    private String summary;
    /** 决策加减分建议（约 -12 ~ +12） */
    private int scoreDelta;
}
