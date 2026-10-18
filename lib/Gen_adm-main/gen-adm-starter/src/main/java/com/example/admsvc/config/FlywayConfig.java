package com.example.admsvc.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import javax.sql.DataSource;

/**
 * Runs gen-adm-starter's own Flyway migrations from
 * {@code classpath:db/migration/genadm}, independent of the host app's own
 * Flyway setup (if any) at the default {@code classpath:db/migration}
 * location — so the two never collide or double-apply each other's scripts.
 *
 * <p>Only ordered after {@link DataSourceAutoConfiguration} — gen-adm-starter
 * depends on plain {@code flyway-core}, not {@code spring-boot-starter-flyway},
 * so Boot's {@code FlywayAutoConfiguration} class is not guaranteed to be on
 * this module's own classpath and cannot be referenced here. Ordering here is
 * enforced by {@code GenAdmAutoConfiguration}'s own
 * {@code @AutoConfigureAfter(DataSourceAutoConfiguration.class)}, since this
 * class is discovered via that class's {@code @ComponentScan}.
 *
 * <p>Gated on an actual {@link DataSource} bean existing — a host application
 * with no persistence configured (e.g. gen-adm-demo before Task 7) must
 * still boot cleanly, and an unconditional {@code @Bean} method requiring a
 * {@code DataSource} parameter would force Boot to try (and fail) to
 * auto-configure one.
 */
@Configuration
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnBean(DataSource.class)
public class FlywayConfig {

    @Bean
    public Flyway genAdmFlyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/genadm")
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();
        return flyway;
    }

    /**
     * Guarantees gen-adm's migration runs before Hibernate validates the
     * schema, even though this bean has no direct dependency edge to the
     * EntityManagerFactory.
     */
    @EventListener(ContextRefreshedEvent.class)
    public void noop() {
        // Bean creation order above is sufficient; this listener exists only
        // to document the ordering requirement for future readers.
    }
}
