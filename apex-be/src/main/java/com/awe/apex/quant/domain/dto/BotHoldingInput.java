package com.awe.apex.quant.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 视觉模型提取的单条持仓。
 */
@Data
public class BotHoldingInput {

    /** 证券代码，可由证券简称唯一匹配后回填 */
    private String code;

    /** 证券简称 */
    private String name;

    /** 持仓数量 */
    @NotNull(message = "持仓数量不能为空")
    private Integer quantity;

    /** 成本价 */
    @NotNull(message = "成本价不能为空")
    private BigDecimal costPrice;

    /**
     * 截图中的该证券市值，可空
     */
    private BigDecimal marketValue;
}
