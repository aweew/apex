package com.awe.apex.common.config;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.convert.LocalDateConverter;
import com.awe.apex.common.convert.LocalDateTimeConverter;
import com.awe.apex.common.convert.LocalTimeConverter;
import com.awe.apex.common.interceptor.LogInterceptor;
import com.awe.apex.common.interceptor.WebInvokeTimeInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * web配置
 *
 * @author Awe
 * @date 2023/4/4 14:03
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final List<String> defaultPath = CollUtil.newArrayList("/**");

    private final List<String> excludePath = Arrays.asList(
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v2/**",
            "/v3/**",
            "/**/doc.html",
            "/webjars/**",
            "/swagger-resources/**",
            "/test/**",
            "/**/login",
            "/csrf"
    );

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 日志拦截器
        registry.addInterceptor(new LogInterceptor()).addPathPatterns("/**");
        // 全局访问性能拦截
        registry.addInterceptor(new WebInvokeTimeInterceptor());
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new LocalDateConverter());
        registry.addConverter(new LocalDateTimeConverter());
        registry.addConverter(new LocalTimeConverter());
    }

    /**
     * 跨域配置
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        // 设置访问源地址
        config.addAllowedOriginPattern("*");
        // 设置访问源请求头
        config.addAllowedHeader("*");
        // 设置访问源请求方法
        config.addAllowedMethod("*");
        // 有效期 1800秒
        config.setMaxAge(1800L);
        // 添加映射路径，拦截一切请求
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        // 返回新的CorsFilter
        return new CorsFilter(source);
    }

}
