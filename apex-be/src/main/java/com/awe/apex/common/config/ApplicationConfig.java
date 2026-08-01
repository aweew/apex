package com.awe.apex.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 应用配置
 *
 * @author Awe
 * @since 2025/9/9 14:49
 */
@AutoConfiguration
@EnableAspectJAutoProxy(exposeProxy = true)
public class ApplicationConfig {

}
