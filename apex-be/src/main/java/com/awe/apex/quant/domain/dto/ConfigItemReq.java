package com.awe.apex.quant.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 配置项更新
 */
@Data
public class ConfigItemReq {

    /**
     * 键
     */
    @NotBlank
    private String configKey;

    /**
     * 值
     */
    @NotBlank
    private String configValue;
}
