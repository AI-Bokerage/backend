package com.company.ppmstarter;

import com.company.ppmsvc.plan.usecase.PlanApplicationService;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * The single public entry point for ppm-core's official Spring Boot
 * integration. Registered via {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * so any Spring Boot application with this starter on its classpath picks
 * it up automatically — no {@code @Import} or manual configuration required
 * on the consumer's side.
 *
 * <p>This class and everything it pulls in ({@link PpmUseCaseAutoConfiguration},
 * {@link PpmPortAvailabilityValidator}) contain zero business logic. Every
 * business rule, validation, and domain decision lives in {@code ppm-core};
 * this module only wires beans and validates that wiring at startup.
 *
 * <p>Active only when:
 * <ul>
 *   <li>ppm-core is on the classpath ({@link PlanApplicationService} resolvable), and</li>
 *   <li>{@code ppm.enabled} is not explicitly set to {@code false} (default {@code true}).</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(PlanApplicationService.class)
@ConditionalOnProperty(prefix = "ppm", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PpmProperties.class)
@Import(PpmUseCaseAutoConfiguration.class)
public class PpmAutoConfiguration {

    @Bean
    public PpmPortAvailabilityValidator ppmPortAvailabilityValidator(ConfigurableListableBeanFactory beanFactory) {
        return new PpmPortAvailabilityValidator(beanFactory);
    }
}
