package com.awe.apex.quant.signal.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 短线市场情绪温度。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortTermEmotionResp {

    /** 温度分0到100。 */
    private Integer score;

    /** 恐慌、中性或贪婪。 */
    private String label;

    /** 情绪数据截至日。 */
    private LocalDate dataAsOf;

    /** 计算依据。 */
    private String basis;
}
