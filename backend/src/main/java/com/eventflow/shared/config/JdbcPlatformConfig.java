package com.eventflow.shared.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class JdbcPlatformConfig implements PlatformConfig {

    private final JdbcTemplate jdbc;

    JdbcPlatformConfig(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int intValue(String key, String field, int fallback) {
        Integer value = jdbc.query(
                "SELECT (value ->> ?)::int FROM ops.global_config WHERE key = ?",
                rs -> rs.next() ? (Integer) rs.getObject(1) : null, field, key);
        return value == null ? fallback : value;
    }
}
