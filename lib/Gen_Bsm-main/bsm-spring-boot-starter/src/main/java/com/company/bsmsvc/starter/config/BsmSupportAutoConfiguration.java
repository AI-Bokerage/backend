package com.company.bsmsvc.starter.config;

import com.company.bsmsvc.application.impl.InvoiceNumberGeneratorImpl;
import com.company.bsmsvc.application.service.InvoiceNumberGenerator;
import com.company.bsmsvc.domain.service.SubscriptionLifecycleMapper;
import com.company.bsmsvc.domain.service.TenantOwnershipValidator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Registers bsm-core's stateless helper domain services — plain classes with no port
 * dependencies, so they need no {@code @ConditionalOnBean} gating, only the ability for a
 * consumer to override with their own bean.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "bsm", name = "enabled", matchIfMissing = true)
public class BsmSupportAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SubscriptionLifecycleMapper subscriptionLifecycleMapper() {
        return new SubscriptionLifecycleMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantOwnershipValidator tenantOwnershipValidator() {
        return new TenantOwnershipValidator();
    }

    @Bean
    @ConditionalOnMissingBean
    public InvoiceNumberGenerator invoiceNumberGenerator() {
        return new InvoiceNumberGeneratorImpl();
    }
}
