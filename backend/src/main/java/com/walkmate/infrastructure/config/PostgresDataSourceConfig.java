package com.walkmate.infrastructure.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Builds the JDBC {@link javax.sql.DataSource} from {@code DB_URL}.
 *
 * <p>Defining the bean explicitly (instead of relying on DataSourceAutoConfiguration)
 * lets {@link PostgresConnectionString} turn a Neon connection string into a valid
 * JDBC URL before HikariCP sees it. Spring Boot's auto-configuration backs off
 * because a DataSource bean already exists.</p>
 */
@Configuration
public class PostgresDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(PostgresDataSourceConfig.class);

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public HikariDataSource dataSource(Environment environment) {
        PostgresConnectionString.Settings settings = PostgresConnectionString.parse(
                environment.getProperty("spring.datasource.url"),
                environment.getProperty("spring.datasource.username"),
                environment.getProperty("spring.datasource.password"));

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(settings.jdbcUrl());
        dataSource.setUsername(settings.username());
        dataSource.setPassword(settings.password());
        dataSource.setDriverClassName("org.postgresql.Driver");

        log.info("PostgreSQL datasource configured for {}", settings.jdbcUrl());
        return dataSource;
    }
}
