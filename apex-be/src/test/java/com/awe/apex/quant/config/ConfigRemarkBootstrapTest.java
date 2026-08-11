package com.awe.apex.quant.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ConfigRemarkBootstrapTest {

    @Test
    void repairsSystemConfigAndRiskRuleRemarks() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ConfigRemarkBootstrap bootstrap = new ConfigRemarkBootstrap();
        ReflectionTestUtils.setField(bootstrap, "jdbcTemplate", jdbcTemplate);

        bootstrap.run(null);

        verify(jdbcTemplate, times(10)).update(
                eq("UPDATE system_config SET remark = ? WHERE config_key = ? AND (remark IS NULL OR remark <> ?)"),
                anyString(), anyString(), anyString());
        verify(jdbcTemplate, times(6)).update(
                eq("UPDATE risk_rule SET remark = ? WHERE rule_key = ? AND (remark IS NULL OR remark <> ?)"),
                anyString(), anyString(), anyString());
        verify(jdbcTemplate).update(anyString(), eq("佣金"), eq("commission_rate"), eq("佣金"));
        verify(jdbcTemplate).update(anyString(), eq("总仓位上限"), eq("total_position_limit"), eq("总仓位上限"));
    }
}
