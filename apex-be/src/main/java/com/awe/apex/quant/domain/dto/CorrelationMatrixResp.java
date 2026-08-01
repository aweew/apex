package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 日收益相关性矩阵
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorrelationMatrixResp {

    /**
     * 代码顺序
     */
    private List<String> codes;

    /**
     * 名称
     */
    private List<String> names;

    /**
     * 相关矩阵（与 codes 同序）
     */
    private List<List<BigDecimal>> matrix;

    /**
     * 样本交易日数
     */
    private Integer sampleDays;

    /**
     * 说明
     */
    private String message;
}
