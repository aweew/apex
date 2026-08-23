package com.awe.apex.quant.config;

import lombok.Data;

import java.util.List;

/**
 * Apex 跨域配置
 */
@Data
public class ApexCorsProperties {

    /**
     * 允许携带凭据访问接口的跨域来源
     */
    private List<String> allowedOriginPatterns;
}
