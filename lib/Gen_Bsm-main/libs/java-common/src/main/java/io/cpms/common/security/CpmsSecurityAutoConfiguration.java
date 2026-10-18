package io.cpms.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Shared Spring Security auto-configuration for CPMS downstream services.
 *
 * <p>Import in each downstream service's {@code @Configuration}:
 * <pre>
 *   {@literal @}Import(CpmsSecurityAutoConfiguration.class)
 * </pre>
 *
 * <p>Provides:
 * <ul>
 *   <li>{@link JwtDecoder} — RS256 validation via auth-svc JWKS</li>
 *   <li>{@link CpmsJwtAuthConverter} — JWT → {@link CpmsAuthenticatedPrincipal}</li>
 *   <li>{@link TenantScopeFilter} — tenant isolation + MDC</li>
 *   <li>{@link RedisRolePermissionResolver} — role:{roleId} Redis Set lookup</li>
 *   <li>{@link PermissionCheckAspect} — enforces {@link RequirePermission}</li>
 * </ul>
 *
 * <p>Requires: a {@link StringRedisTemplate} bean (from any service that has
 * {@code spring-boot-starter-data-redis} configured).
 */
@Configuration
@EnableConfigurationProperties(CpmsSecurityProperties.class)
public class CpmsSecurityAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CpmsSecurityAutoConfiguration.class);

    @Bean
    public JwtDecoder cpmsJwtDecoder(CpmsSecurityProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(props.getJwksUri())
                .build();
        OAuth2TokenValidator<Jwt> issuerValidator =
                JwtValidators.createDefaultWithIssuer(props.getIssuer());
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator));
        log.info("cpms.security.jwt.initialized jwksUri={} issuer={}", props.getJwksUri(), props.getIssuer());
        return decoder;
    }

    @Bean
    public CpmsJwtAuthConverter cpmsJwtAuthConverter() {
        return new CpmsJwtAuthConverter();
    }

    @Bean
    public TenantScopeFilter tenantScopeFilter() {
        return new TenantScopeFilter();
    }

    @Bean
    public RolePermissionResolver rolePermissionResolver(StringRedisTemplate redisTemplate) {
        log.info("cpms.security.rbac.initialized resolver=RedisRolePermissionResolver");
        return new RedisRolePermissionResolver(redisTemplate);
    }

    @Bean
    public PermissionCheckAspect permissionCheckAspect(RolePermissionResolver rolePermissionResolver) {
        return new PermissionCheckAspect(rolePermissionResolver);
    }
}
