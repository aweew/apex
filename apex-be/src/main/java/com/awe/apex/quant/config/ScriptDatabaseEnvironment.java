package com.awe.apex.quant.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Passes the active Spring datasource connection to Python sync scripts.
 */
@Component
public class ScriptDatabaseEnvironment {

    private static final Pattern MYSQL_JDBC_URL = Pattern.compile(
            "^jdbc:(?:p6spy:)?mysql://([^/:?#]+)(?::(\\d+))?/([^?;]+)");

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${spring.datasource.username:}")
    private String datasourceUsername;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    public void apply(Map<String, String> environment) {
        apply(environment, datasourceUrl, datasourceUsername, datasourcePassword);
    }

    static void apply(Map<String, String> environment, String jdbcUrl, String username, String password) {
        if (environment == null || jdbcUrl == null) {
            return;
        }
        Matcher matcher = MYSQL_JDBC_URL.matcher(jdbcUrl.trim());
        if (!matcher.find()) {
            return;
        }
        environment.put("MYSQL_HOST", matcher.group(1));
        environment.put("MYSQL_PORT", matcher.group(2) != null ? matcher.group(2) : "3306");
        environment.put("MYSQL_DB", matcher.group(3));
        environment.put("MYSQL_USER", username != null ? username : "");
        environment.put("MYSQL_PASSWORD", password != null ? password : "");
    }
}
