package com.example.authsvc.config;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Makes every {@code @Service}/{@code @RestController}/{@code @Repository}/
 * {@code @ConfigurationProperties} class in the {@code com.example.authsvc}
 * package tree visible to a host application, regardless of that application's
 * own base package.
 *
 * <p>A host app's default component scan (rooted at its own
 * {@code @SpringBootApplication} class's package) would never reach classes
 * living in a dependency jar under a different package — this explicit
 * {@code @ComponentScan}/{@code @EntityScan}/{@code @EnableJpaRepositories}/
 * {@code @ConfigurationPropertiesScan} is what makes this a real,
 * host-package-independent Spring Boot starter rather than something that
 * only happens to work when co-located in the same package. The last of
 * these matters because every {@code @ConfigurationProperties} class here
 * ({@code JwtProperties}, {@code CookieProperties}, {@code CorsProperties},
 * {@code AuthBehaviorProperties}, {@code AwsProperties}) is a plain class with
 * no {@code @Component} — they rely entirely on being scanned, not on being
 * beans in their own right.
 *
 * <p>({@code @EntityScan} lives at {@code org.springframework.boot.persistence.autoconfigure}
 * in Spring Boot 4, not {@code org.springframework.boot.autoconfigure.domain} — same
 * module-split relocation as {@code EntityManagerFactoryDependsOnPostProcessor} in Task 4.)
 *
 * <p>{@code @AutoConfigureBefore(TaskExecutionAutoConfiguration.class)} avoids a
 * component-scan race: this class is itself an {@code @AutoConfiguration}, processed
 * through Spring Boot's deferred-import mechanism alongside Boot's own auto-configurations,
 * with no ordering constraint between the two by default. {@code TaskExecutionAutoConfiguration}
 * transitively imports {@code TaskExecutorConfigurations$AsyncConfigurerConfiguration}, which
 * is gated by {@code @ConditionalOnMissingBean(AsyncConfigurer.class)} — if that condition is
 * evaluated before this class's {@code @ComponentScan} has registered the starter's own
 * {@code AsyncConfig} (which implements {@code AsyncConfigurer}), Boot registers its own
 * default {@code AsyncConfigurer} too. With {@code spring.main.allow-bean-definition-overriding}
 * enabled (as in the demo host app), the starter's {@code AsyncConfig} bean definition then
 * silently collides with Boot's already-registered one instead of Spring's normal duplicate-
 * bean-definition safety check ever firing, corrupting the {@code authAsync} executor bean's
 * cached factory-method reference (observed as {@code IllegalArgumentException: object is not
 * an instance of declaring class} when gen-auth-demo boots). Forcing this class to be
 * processed first guarantees {@code AsyncConfig} is already registered by the time Boot's
 * conditional check runs.
 *
 * <p>{@code ServletWebSecurityAutoConfiguration} is listed for the same class of reason:
 * its nested {@code SecurityFilterChainConfiguration} backs off via
 * {@code @ConditionalOnDefaultWebSecurity} (which includes a
 * {@code @ConditionalOnMissingBean(SecurityFilterChain.class)} check) only if this
 * starter's own {@code SecurityConfig} — which defines the real {@code SecurityFilterChain}
 * bean — has already been registered by the time that condition is evaluated. Today this
 * happens to work by deferred-import ordering luck, not by a guarantee; without this
 * ordering hint, Boot's default HTTP Basic/form-login filter chain could win the race and
 * silently replace this starter's security configuration in a host app that hasn't defined
 * its own {@code SecurityFilterChain} bean.
 *
 * <p>Discovered automatically via {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 */
@AutoConfiguration
@AutoConfigureBefore({ TaskExecutionAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class })
@ComponentScan("com.example.authsvc")
@EntityScan("com.example.authsvc.infrastructure.persistence.entity")
@EnableJpaRepositories("com.example.authsvc.infrastructure.persistence.repository")
@ConfigurationPropertiesScan("com.example.authsvc")
public class GenAuthAutoConfiguration {
}
