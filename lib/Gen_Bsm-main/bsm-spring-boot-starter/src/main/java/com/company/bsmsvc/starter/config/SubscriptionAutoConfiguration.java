package com.company.bsmsvc.starter.config;

import com.company.bsmsvc.application.impl.SubscriptionServiceImpl;
import com.company.bsmsvc.application.impl.SubscriptionSynchronizationServiceImpl;
import com.company.bsmsvc.application.service.PaymentGatewayResolver;
import com.company.bsmsvc.application.service.SubscriptionService;
import com.company.bsmsvc.application.service.SubscriptionSynchronizationService;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.port.PaymentMethodRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionEventPublisherPort;
import com.company.bsmsvc.domain.port.SubscriptionEventRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionHistoryRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionScheduleRepositoryPort;
import com.company.bsmsvc.domain.port.TenantScopePort;
import com.company.bsmsvc.domain.port.TenantTrialRecordRepositoryPort;
import com.company.bsmsvc.domain.port.UsageLimitsSeedingPort;
import com.company.bsmsvc.domain.port.UserUsagePort;
import com.company.bsmsvc.domain.service.SubscriptionLifecycleMapper;
import com.company.bsmsvc.domain.service.TenantOwnershipValidator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Anchor port: {@link SubscriptionRepositoryPort}. The full aggregate additionally requires
 * {@link SubscriptionHistoryRepositoryPort}, {@link SubscriptionEventRepositoryPort},
 * {@link SubscriptionScheduleRepositoryPort}, {@link TenantTrialRecordRepositoryPort},
 * {@link UsageLimitsSeedingPort}, {@link UserUsagePort}, {@link TenantScopePort},
 * {@link SubscriptionEventPublisherPort}, {@link PaymentMethodRepositoryPort}, and
 * {@link PaymentGatewayResolver} (for provider synchronization).
 */
@AutoConfiguration
@AutoConfigureAfter({TenantBillingProfileAutoConfiguration.class, BsmSupportAutoConfiguration.class})
@ConditionalOnProperty(prefix = "bsm", name = "enabled", matchIfMissing = true)
@ConditionalOnBean({
    SubscriptionRepositoryPort.class, SubscriptionHistoryRepositoryPort.class,
    SubscriptionEventRepositoryPort.class, SubscriptionScheduleRepositoryPort.class,
    TenantTrialRecordRepositoryPort.class, UsageLimitsSeedingPort.class, UserUsagePort.class,
    TenantScopePort.class, SubscriptionEventPublisherPort.class, PaymentMethodRepositoryPort.class,
    PaymentGatewayResolver.class, TenantBillingProfileService.class
})
public class SubscriptionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SubscriptionSynchronizationService subscriptionSynchronizationService(
            TenantBillingProfileService billingProfileService,
            PaymentGatewayResolver resolver,
            SubscriptionRepositoryPort subscriptionRepository,
            SubscriptionHistoryRepositoryPort subscriptionHistoryRepository,
            PaymentMethodRepositoryPort paymentMethodRepository) {
        return new SubscriptionSynchronizationServiceImpl(
            billingProfileService, resolver, subscriptionRepository,
            subscriptionHistoryRepository, paymentMethodRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public SubscriptionService subscriptionService(
            SubscriptionRepositoryPort subscriptionRepository,
            SubscriptionHistoryRepositoryPort subscriptionHistoryRepository,
            SubscriptionEventRepositoryPort subscriptionEventRepository,
            SubscriptionScheduleRepositoryPort subscriptionScheduleRepository,
            SubscriptionLifecycleMapper subscriptionLifecycleMapper,
            TenantOwnershipValidator tenantOwnershipValidator,
            SubscriptionSynchronizationService subscriptionSynchronizationService,
            UsageLimitsSeedingPort usageLimitsSeedingPort,
            TenantScopePort tenantScopePort,
            SubscriptionEventPublisherPort subscriptionEventPublisherPort,
            TenantTrialRecordRepositoryPort tenantTrialRecordRepository,
            UserUsagePort userUsagePort) {
        return new SubscriptionServiceImpl(
            subscriptionRepository, subscriptionHistoryRepository, subscriptionEventRepository,
            subscriptionScheduleRepository, subscriptionLifecycleMapper, tenantOwnershipValidator,
            subscriptionSynchronizationService, usageLimitsSeedingPort, tenantScopePort,
            subscriptionEventPublisherPort, tenantTrialRecordRepository, userUsagePort);
    }
}
