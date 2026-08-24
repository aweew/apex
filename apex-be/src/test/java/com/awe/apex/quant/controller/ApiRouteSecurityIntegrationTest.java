package com.awe.apex.quant.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP 路由认证边界回归测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiRouteSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    /**
     * 遗留认证和管理入口不能继续发布。
     */
    @Test
    void shouldNotPublishRetiredLegacyEndpoints() throws Exception {
        Set<String> patterns = requestMappingHandlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(mapping -> mapping.getPatternValues().stream())
                .collect(java.util.stream.Collectors.toSet());

        assertFalse(patterns.contains("/user/doLogin"));
        assertFalse(patterns.contains("/auth/login"));
        assertFalse(patterns.contains("/sys/user/list"));

        mockMvc.perform(get("/user/doLogin")
                        .param("username", "zhang")
                        .param("password", "123456"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/sys/user/list"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 业务 API 必须默认要求登录态。
     */
    @Test
    void shouldRequireLoginForBusinessApi() throws Exception {
        mockMvc.perform(get("/api/decision/today"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 存活探针保留匿名访问能力。
     */
    @Test
    void shouldKeepHealthProbePublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.app").value("apex"))
                .andExpect(jsonPath("$.data.status").value("UP"));
        mockMvc.perform(get("/api/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.app").value("apex"))
                .andExpect(jsonPath("$.data.databaseStatus").value("UP"))
                .andExpect(jsonPath("$.data.redisStatus").value("UP"));
    }
}
