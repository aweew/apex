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

    /** 证券代码 */
    private String code;
    /** 估值档位 */
    private String level;
    /** 估值中文标签 */
    private String levelLabel;
    /** 估值综合分 */
    private BigDecimal score;
    /** 滚动市盈率 */
    private BigDecimal peTtm;
    /** 市净率 */
    private BigDecimal pb;
    /** 市盈率行业分位 */
    private BigDecimal pePercentile;
    /** 市盈率相对增长比 */
    private BigDecimal peg;
    /** 安全边际 */
    private BigDecimal marginOfSafety;
    /** 估值摘要 */
    private String summary;
    /** 决策加减分建议 */
    private int scoreDelta;
    /** 营收利润同步增长且财务质量达标 */
    private Boolean growthQualityVerified;
}
