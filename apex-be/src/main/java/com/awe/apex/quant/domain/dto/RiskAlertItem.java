package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 风控告警项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAlertItem {

    /**
     * INFO / WARN / CRITICAL
     */
    private String level;

    /**
     * 类别：POSITION / STOP / INDUSTRY / DATA
     */
    private String category;

    /**
     * 相关代码，可空
     */
    private String code;

    /**
     * 文案
     */
    private String message;
}
