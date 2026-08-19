package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 持仓截图预览行
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioImageImportRowResp {

    /**
     * 可用于导入的证券代码，无法匹配时保留识别原文
     */
    private String security;

    /**
     * 股票库中的证券代码
     */
    private String code;

    /**
     * 股票库中的证券名称
     */
    private String name;

    /**
     * 持仓数量
     */
    private String quantity;

    /**
     * 成本价
     */
    private String costPrice;

    /**
     * 截图识别市值
     */
    private BigDecimal marketValue;

    /**
     * 模型识别置信度
     */
    private BigDecimal confidence;

    /**
     * 是否可以进入确认导入
     */
    private Boolean valid;

    /**
     * 识别或校验提示
     */
    private String warning;
}
