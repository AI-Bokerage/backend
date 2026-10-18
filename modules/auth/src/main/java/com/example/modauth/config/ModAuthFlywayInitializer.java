package com.example.modauth.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Runs this module's own migrations (role/terms/invitation tables) against
 * the shared DataSource, using the Flyway default history table
 * ({@code flyway_schema_history}) and its own dedicated location
 * ({@code classpath:db/migration/modauth}) — kept separate from
 * gen-auth-starter's own independent Flyway run (table
 * {@code flyway_schema_history_auth}, location
 * {@code classpath:db/migration/genauth}) so neither migration set can
 * collide with the other.
 *
 * <p>Flyway's classpath scan for a location is recursive, so a plain
 * {@code classpath:db/migration} here would also pick up the starter's own
 * {@code db/migration/genauth/V1__init.sql} from the same jar and fail with
 * "Found more than one migration with version 1" — hence the dedicated
 * {@code modauth} subfolder, mirroring the starter's own {@code genauth} one.
 *
 * <p>{@code @DependsOn("genAuthFlywayInitializer")} isn't required for
 * correctness (this module's tables carry no FK into auth_users), but keeps
 * migration order predictable: the starter's schema exists first.
 */
@Slf4j
@Component
@DependsOn("genAuthFlywayInitializer")
@RequiredArgsConstructor
public class ModAuthFlywayInitializer implements InitializingBean {

    private final DataSource dataSource;

    @Override
    public void afterPropertiesSet() {
        log.info("modauth.flyway.migrating");
        Flyway.configure()
                .dataSource(dataSource)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .validateOnMigrate(true)
                .locations("classpath:db/migration/modauth")
                .load()
                .migrate();
        log.info("modauth.flyway.migrated");
    }
}
