package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 涨停赚钱效应（相对前一日池）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LimitUpEffectResp {

    /**
     * 前一日涨停家数
     */
    private Integer prevCount;

    /**
     * 晋级成功家数
     */
    private Integer promoteOk;

    /**
     * 晋级失败家数（昨涨停今日断板）
     */
    private Integer promoteFail;

    /**
     * 同板续涨停（未晋级但也未断板）
     */
    private Integer promoteHold;

    /**
     * 整体晋级率%（晋级 / 昨涨停）
     */
    private BigDecimal promoteRate;

    /**
     * 昨涨停今日平均涨跌幅%
     */
    private BigDecimal avgNextPct;

    /**
     * 晋级失败示例（名称）
     */
    private List<String> failNames;

    /**
     * 说明
     */
    private String message;
}
