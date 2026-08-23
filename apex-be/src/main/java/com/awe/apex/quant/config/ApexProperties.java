package com.awe.apex.quant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Apex 业务配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "apex")
public class ApexProperties {

    /**
     * 妙想输出目录
     */
    private String mxOutputDir;

    /**
     * 跨域访问配置
     */
    private ApexCorsProperties cors = new ApexCorsProperties();
}
