package com.example.admsvc.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * Makes every {@code @Service}/{@code @RestController}/{@code @Repository}/
 * {@code @ConfigurationProperties} class in the {@code com.example.admsvc}
 * package tree visible to a host application, regardless of that
 * application's own base package. Discovered automatically via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 *
 * <p>{@code @EntityScan} is unconditional — it is pure classpath metadata and
 * never forces a {@link DataSource} into existence. JPA repository
 * activation is deliberately split into the nested {@link
 * JpaRepositoriesConfiguration}, gated on an actual {@code DataSource} bean,
 * so this module's own beans never demand a datasource that isn't there.
 *
 * <p><b>Known limitation (see task-2-report.md):</b> this gate does not fully
 * insulate a host application with zero persistence configuration (e.g.
 * gen-adm-demo before Task 7) — Spring Boot's own {@code HibernateJpaAutoConfiguration}
 * eagerly builds an {@code EntityManagerFactory} the moment a pooled
 * {@code DataSource} implementation (Hikari, pulled in by
 * {@code spring-boot-starter-data-jpa}) is on the classpath at all, independent
 * of whether {@code @EnableJpaRepositories} is active. That is Spring Boot's
 * own auto-configuration, outside this class's control, and can only be
 * avoided by the host application either configuring a datasource or
 * excluding {@code DataSourceAutoConfiguration}/{@code HibernateJpaAutoConfiguration}
 * itself.
 *
 * <p>{@code @EnableTransactionManagement(order = HIGHEST_PRECEDENCE)} pins
 * Spring's {@code @Transactional} proxy advice as the outermost advisor
 * around every {@code application.impl} method. Without this, it and {@code
 * TenantContextAspect} both default to {@code LOWEST_PRECEDENCE}, leaving
 * their relative nesting undefined — if the transaction advice ended up
 * nested inside the tenant-context aspect instead of wrapping it, {@code
 * set_config('app.tenant_id', ...)} could run before the transaction opens
 * (or with none active), silently defeating RLS. Pinning this here means
 * {@code TenantContextAspect} needs no explicit {@code @Order}: it naturally
 * nests inside, running after the transaction starts and before the
 * repository call.
 */
@AutoConfiguration
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ComponentScan("com.example.admsvc")
@EntityScan("com.example.admsvc.infrastructure.persistence.entity")
@ConfigurationPropertiesScan("com.example.admsvc")
@EnableTransactionManagement(order = Ordered.HIGHEST_PRECEDENCE)
public class GenAdmAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(DataSource.class)
    @EnableJpaRepositories("com.example.admsvc.infrastructure.persistence.repository")
    public static class JpaRepositoriesConfiguration {
    }
}
