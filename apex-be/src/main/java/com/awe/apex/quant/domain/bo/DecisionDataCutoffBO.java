package com.awe.apex.quant.domain.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 决策运行数据截止信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionDataCutoffBO {

    /**
     * 决策运行可见信息截止时间。
     */
    private LocalDateTime asOfTime;

    /**
     * 数据读取策略。
     */
    private String policy;

    /**
     * 市场行情实际截至日。
     */
    private LocalDate marketDataAsOf;
}
