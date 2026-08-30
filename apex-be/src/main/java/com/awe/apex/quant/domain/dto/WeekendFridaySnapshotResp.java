package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 周五收盘市场快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeekendFridaySnapshotResp {

    /** 数据截至日。 */
    private LocalDate asOf;

    /** 市场立场。 */
    private String stance;

    /** 三市成交额。 */
    private BigDecimal indexVolume;

    /** 成交额较前一日变化。 */
    private BigDecimal indexVolumeChange;

    /** 成交量趋势文案。 */
    private String volumeLabel;

    /** 上涨家数。 */
    private Integer breadthUp;

    /** 下跌家数。 */
    private Integer breadthDown;

    /** 平盘家数。 */
    private Integer breadthFlat;

    /** 涨停家数。 */
    private Integer limitUpCount;

    /** 跌停家数。 */
    private Integer limitDownCount;

    /** 热点主题。 */
    @Builder.Default
    private List<String> hotThemes = new ArrayList<>();
}
