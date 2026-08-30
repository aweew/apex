package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 下周交易主线及其可证伪条件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeekendTradingThemeResp {

    /** 主线名称。 */
    private String theme;

    /** 消息催化。 */
    private String catalyst;

    /** 盘面确认条件。 */
    private String confirmation;

    /** 失效条件。 */
    private String invalidation;

    /** 关联证券或行业。 */
    private String relatedCodes;
}
