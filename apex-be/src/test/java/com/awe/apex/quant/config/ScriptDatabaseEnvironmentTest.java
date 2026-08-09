package com.awe.apex.quant.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScriptDatabaseEnvironmentTest {

    @Test
    void appliesMysqlSettingsFromP6spyJdbcUrl() {
        Map<String, String> env = new HashMap<>();

        ScriptDatabaseEnvironment.apply(
                env,
                "jdbc:p6spy:mysql://100.71.129.75:3306/apex?useUnicode=true",
                "root",
                "secret");

        assertEquals("100.71.129.75", env.get("MYSQL_HOST"));
        assertEquals("3306", env.get("MYSQL_PORT"));
        assertEquals("apex", env.get("MYSQL_DB"));
        assertEquals("root", env.get("MYSQL_USER"));
        assertEquals("secret", env.get("MYSQL_PASSWORD"));
    }
}
