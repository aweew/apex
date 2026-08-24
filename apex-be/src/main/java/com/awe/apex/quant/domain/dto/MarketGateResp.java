package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 市场环境门控结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketGateResp {

    /** 状态 AVAILABLE/MISSING */
    private String status;

    /** 市场状态 OFFENSIVE/BALANCED/DEFENSIVE */
    private String level;

    /** 市场状态标签 */
    private String label;

    /** 数据截至日期 */
    private LocalDate asOf;

    /** 数据说明 */
    private String reason;
}
