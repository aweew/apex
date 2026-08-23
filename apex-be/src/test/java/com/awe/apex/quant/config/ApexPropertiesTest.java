package com.awe.apex.quant.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApexPropertiesTest {

    @Test
    void bindsCorsOriginPatternsFromNestedConfiguration() {
        ApexProperties properties = new ApexProperties();
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "apex.cors.allowed-origin-patterns[0]", "https://app.example.com",
                "apex.cors.allowed-origin-patterns[1]", "https://admin.example.com"));

        new Binder(source).bind("apex", Bindable.ofInstance(properties));

        assertEquals(List.of("https://app.example.com", "https://admin.example.com"),
                properties.getCors().getAllowedOriginPatterns());
    }
}
