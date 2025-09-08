package com.demo.credit.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseHealthStartupCheck implements ApplicationRunner {

    private final DataSource dataSource;
    private final Environment env;

    @Override
    public void run(ApplicationArguments args) {
        boolean requireDb = env.getProperty("app.require-database", Boolean.class, true);
        String probe = env.getProperty("app.db.validation-query", "SELECT 1");

        try (Connection c = DataSourceUtils.getConnection(dataSource);
             Statement s = c.createStatement()) {
            s.execute(probe);
            log.info("✅ Database connectivity OK at startup.");
        } catch (Exception e) {
            String msg = "❌ Database connectivity FAILED at startup.";
            if (requireDb) {
                log.error(msg, e);
                throw new IllegalStateException(msg, e);
            } else {
                log.error(msg + " (app.require-database=false => app vẫn chạy)", e);
            }
        }
    }
}
