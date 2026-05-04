package com.smartcampus.navigation.common;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (tableExists("poi") && !columnExists("poi", "image_url")) {
            jdbcTemplate.execute("ALTER TABLE poi ADD COLUMN image_url VARCHAR(500) NOT NULL DEFAULT '' AFTER source_url");
        }
        if (tableExists("discover_post") && !tableExists("discover_post_image")) {
            jdbcTemplate.execute("""
                    CREATE TABLE discover_post_image (
                      id BIGINT PRIMARY KEY AUTO_INCREMENT,
                      post_id BIGINT NOT NULL,
                      image_url VARCHAR(500) NOT NULL,
                      sort_order INT NOT NULL DEFAULT 0,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      INDEX idx_discover_post_image_post (post_id)
                    )
                    """);
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
    }
}
