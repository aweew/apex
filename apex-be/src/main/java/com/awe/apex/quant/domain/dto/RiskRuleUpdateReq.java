package com.awe.apex.quant.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 风控规则更新
 */
@Data
public class RiskRuleUpdateReq {

    /**
     * 规则键
     */
    @NotBlank
    private String ruleKey;

    /**
     * 规则值
     */
    @NotBlank
    private String ruleValue;
}
