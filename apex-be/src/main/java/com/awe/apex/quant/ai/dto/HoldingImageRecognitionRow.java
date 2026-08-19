package com.awe.apex.quant.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 大模型识别出的单条持仓
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldingImageRecognitionRow {

    /**
     * 证券代码
     */
    private String code;

    /**
     * 证券名称
     */
    private String name;

    /**
     * 持仓数量原文
     */
    private String quantity;

    /**
     * 成本价原文
     */
    private String costPrice;

    /**
     * 市值原文
     */
    private String marketValue;

    /**
     * 模型识别置信度
     */
    private BigDecimal confidence;
}
