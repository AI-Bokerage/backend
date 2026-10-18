package com.company.ppmsvc.config;

import com.company.ppmsvc.infrastructure.security.PermitAllPpmAuthorizationService;
import com.company.ppmsvc.infrastructure.security.PlatformPpmAuthorizationService;
import com.company.ppmsvc.security.PpmAuthorizationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires {@link PpmAuthorizationService} for this deployment.
 *
 * <p>This platform explicitly opts into {@link PlatformPpmAuthorizationService}
 * below, preserving its existing SUPER_ADMIN rules unchanged. A different
 * consuming application that defines no {@code PpmAuthorizationService} bean
 * of its own falls back to {@link PermitAllPpmAuthorizationService} — the
 * library's permissive default — via {@code @ConditionalOnMissingBean}.
 */
@Configuration
public class PpmAuthorizationConfig {

    @Bean
    public PpmAuthorizationService platformPpmAuthorizationService() {
        return new PlatformPpmAuthorizationService();
    }

    @Bean
    @ConditionalOnMissingBean(PpmAuthorizationService.class)
    public PpmAuthorizationService defaultPpmAuthorizationService() {
        return new PermitAllPpmAuthorizationService();
    }
}
