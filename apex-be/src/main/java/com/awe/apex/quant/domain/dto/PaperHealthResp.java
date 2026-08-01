package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 模拟盘健康分
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaperHealthResp {

    /**
     * 总分 0~100
     */
    private BigDecimal score;

    /**
     * 等级 A/B/C/D
     */
    private String grade;

    /**
     * 分项说明
     */
    private List<String> factors;

    /**
     * 说明
     */
    private String message;
}
