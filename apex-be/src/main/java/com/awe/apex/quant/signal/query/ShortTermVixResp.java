package com.awe.apex.quant.signal.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * VIX外部风险偏好代理。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortTermVixResp {

    /** VIX是否成功获取。 */
    private boolean available;

    /** VIX原值。 */
    private BigDecimal value;

    /** 反向温度分0到100。 */
    private Integer score;

    /** 恐慌、中性或贪婪。 */
    private String label;

    /** 报价时间。 */
    private LocalDateTime quoteTime;

    /** 行情来源。 */
    private String source;

    /** 口径说明。 */
    private String note;
}
