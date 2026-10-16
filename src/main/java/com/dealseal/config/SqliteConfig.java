package com.dealseal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;
import java.nio.file.*;

@Configuration
public class SqliteConfig {
    @Bean
    DataSource dataSource(@Value("${dealseal.db.path:./data/dealseal.db}") String dbPath) throws Exception {
        Path file = Path.of(dbPath).toAbsolutePath().normalize();
        Files.createDirectories(file.getParent());
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName("org.sqlite.JDBC");
        source.setUrl("jdbc:sqlite:" + file);
        return source;
    }
}
