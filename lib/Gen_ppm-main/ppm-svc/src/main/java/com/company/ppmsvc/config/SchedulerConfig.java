package com.company.ppmsvc.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configures ShedLock for distributed scheduler coordination.
 *
 * <p>Ensures monitoring workers execute on exactly one pod at a time across a
 * multi-instance deployment.  Lock state is stored in the {@code shedlock} table
 * (V001 migration).
 *
 * <p>Conditional on {@code ppm.scheduling.enabled=true} (default: true) so that
 * integration tests can disable automatic background scheduling by setting
 * {@code ppm.scheduling.enabled=false} in their test profile configuration.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT14M")
@ConditionalOnProperty(name = "ppm.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulerConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build()
        );
    }
}
