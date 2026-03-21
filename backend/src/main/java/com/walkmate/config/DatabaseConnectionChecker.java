package com.walkmate.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.sql.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class DatabaseConnectionChecker implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConnectionChecker.class);

    @Autowired
    private DataSource dataSource;

    @Override
    public void run(String... args) {
        try (Connection conn = dataSource.getConnection()) {
            logger.info("✅ Connect database successfully!");
        } catch (Exception e) {
            logger.error("❌ Connect database failed: {}", e.getMessage());
        }
    }
}
