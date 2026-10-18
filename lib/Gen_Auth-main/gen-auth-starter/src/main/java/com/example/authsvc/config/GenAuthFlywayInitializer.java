// gen-auth-starter/src/main/java/com/example/authsvc/config/GenAuthFlywayInitializer.java
package com.example.authsvc.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Runs Gen_AUTH's own Flyway migrations against the host application's DataSource,
 * completely independent of however the host manages its own schema (Flyway,
 * Liquibase, or nothing at all). Uses a dedicated history table
 * ({@code flyway_schema_history_auth}) and a non-default classpath location
 * ({@code classpath:db/migration/genauth}) so it never collides with the host's
 * own migration tooling.
 *
 * <p>Must run before JPA's {@code EntityManagerFactory} bean validates the schema —
 * see {@link GenAuthJpaDependsOnFlywayConfig}, which enforces that ordering.
 */
@Slf4j
@Component("genAuthFlywayInitializer")
@RequiredArgsConstructor
public class GenAuthFlywayInitializer implements InitializingBean {

    private final DataSource dataSource;

    @Override
    public void afterPropertiesSet() {
        log.info("genauth.flyway.migrating");
        Flyway.configure()
                .dataSource(dataSource)
                .table("flyway_schema_history_auth")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .validateOnMigrate(true)
                .locations("classpath:db/migration/genauth")
                .load()
                .migrate();
        log.info("genauth.flyway.migrated");
    }
}
