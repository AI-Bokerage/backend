package com.company.bsmsvc.starter.config;

import com.company.bsmsvc.application.impl.TenantBillingProfileServiceImpl;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.port.TenantBillingProfileRepositoryPort;
import com.company.bsmsvc.domain.port.TenantScopePort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Anchor port: {@link TenantBillingProfileRepositoryPort}. Also requires {@link TenantScopePort}.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "bsm", name = "enabled", matchIfMissing = true)
@ConditionalOnBean({TenantBillingProfileRepositoryPort.class, TenantScopePort.class})
public class TenantBillingProfileAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TenantBillingProfileService tenantBillingProfileService(
            TenantBillingProfileRepositoryPort repository,
            TenantScopePort tenantScopePort) {
        return new TenantBillingProfileServiceImpl(repository, tenantScopePort);
    }
}
