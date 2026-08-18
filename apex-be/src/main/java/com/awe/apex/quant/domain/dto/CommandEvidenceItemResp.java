package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 盘前结论依据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandEvidenceItemResp {

    /**
     * 依据标签。
     */
    private String label;

    /**
     * 依据展示值。
     */
    private String value;

    /**
     * POSITIVE、NEUTRAL 或 NEGATIVE 信号。
     */
    private String signal;
}
