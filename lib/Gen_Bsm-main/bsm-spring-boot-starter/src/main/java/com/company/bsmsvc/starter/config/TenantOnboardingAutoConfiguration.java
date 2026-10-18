package com.company.bsmsvc.starter.config;

import com.company.bsmsvc.application.impl.TenantOnboardingServiceImpl;
import com.company.bsmsvc.application.service.InvoiceGenerationService;
import com.company.bsmsvc.application.service.InvoiceService;
import com.company.bsmsvc.application.service.SubscriptionService;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.application.service.TenantOnboardingService;
import com.company.bsmsvc.domain.model.TrialPolicy;
import com.company.bsmsvc.domain.port.DefaultTrialPlanPort;
import com.company.bsmsvc.domain.port.PlanVersionMetaPort;
import com.company.bsmsvc.domain.port.PpmPricingService;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Anchor ports: {@link DefaultTrialPlanPort} + {@link PlanVersionMetaPort} + {@link PpmPricingService}
 * — the three external catalog/pricing capabilities tenant onboarding cannot function without.
 */
@AutoConfiguration
@AutoConfigureAfter({SubscriptionAutoConfiguration.class, InvoiceAutoConfiguration.class, BsmPolicyAutoConfiguration.class})
@ConditionalOnProperty(prefix = "bsm", name = "enabled", matchIfMissing = true)
@ConditionalOnBean({
    SubscriptionRepositoryPort.class, SubscriptionService.class, DefaultTrialPlanPort.class,
    PlanVersionMetaPort.class, InvoiceGenerationService.class, InvoiceService.class,
    TenantBillingProfileService.class, PpmPricingService.class
})
public class TenantOnboardingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TenantOnboardingService tenantOnboardingService(
            SubscriptionRepositoryPort subscriptionRepository,
            SubscriptionService subscriptionService,
            DefaultTrialPlanPort defaultTrialPlanPort,
            PlanVersionMetaPort planVersionMetaPort,
            InvoiceGenerationService invoiceGenerationService,
            InvoiceService invoiceService,
            TenantBillingProfileService billingProfileService,
            PpmPricingService ppmPricingService,
            TrialPolicy trialPolicy) {
        return new TenantOnboardingServiceImpl(
            subscriptionRepository, subscriptionService, defaultTrialPlanPort, planVersionMetaPort,
            invoiceGenerationService, invoiceService, billingProfileService, ppmPricingService,
            trialPolicy);
    }
}
