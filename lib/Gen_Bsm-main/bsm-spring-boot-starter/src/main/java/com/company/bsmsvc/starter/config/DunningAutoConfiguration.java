package com.company.bsmsvc.starter.config;

import com.company.bsmsvc.application.impl.DunningServiceImpl;
import com.company.bsmsvc.application.service.DunningService;
import com.company.bsmsvc.application.service.InvoiceService;
import com.company.bsmsvc.application.service.PaymentGatewayResolver;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.model.DunningPolicy;
import com.company.bsmsvc.domain.port.BillingLedgerRepositoryPort;
import com.company.bsmsvc.domain.port.DunningAttemptRepositoryPort;
import com.company.bsmsvc.domain.port.DunningEventPublisher;
import com.company.bsmsvc.domain.port.EventPublisherPort;
import com.company.bsmsvc.domain.port.PaymentMethodRepositoryPort;
import com.company.bsmsvc.domain.port.PaymentRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionEventPublisherPort;
import com.company.bsmsvc.domain.port.SubscriptionHistoryRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import com.company.bsmsvc.domain.port.TenantScopePort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/** Anchor port: {@link DunningAttemptRepositoryPort}. */
@AutoConfiguration
@AutoConfigureAfter({InvoiceAutoConfiguration.class, SubscriptionAutoConfiguration.class, BsmPolicyAutoConfiguration.class})
@ConditionalOnProperty(prefix = "bsm", name = "enabled", matchIfMissing = true)
@ConditionalOnBean({
    DunningAttemptRepositoryPort.class, DunningEventPublisher.class, SubscriptionRepositoryPort.class,
    SubscriptionHistoryRepositoryPort.class, PaymentMethodRepositoryPort.class, PaymentRepositoryPort.class,
    BillingLedgerRepositoryPort.class, TenantBillingProfileService.class, PaymentGatewayResolver.class,
    InvoiceService.class, TenantScopePort.class, SubscriptionEventPublisherPort.class, EventPublisherPort.class
})
public class DunningAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DunningService dunningService(
            DunningEventPublisher dunningEventPublisher,
            DunningAttemptRepositoryPort dunningAttemptRepository,
            SubscriptionRepositoryPort subscriptionRepository,
            SubscriptionHistoryRepositoryPort subscriptionHistoryRepository,
            PaymentMethodRepositoryPort paymentMethodRepository,
            PaymentRepositoryPort paymentRepository,
            BillingLedgerRepositoryPort ledgerRepository,
            TenantBillingProfileService billingProfileService,
            PaymentGatewayResolver resolver,
            InvoiceService invoiceService,
            DunningPolicy dunningPolicy,
            TenantScopePort tenantScopePort,
            SubscriptionEventPublisherPort subscriptionEventPublisherPort,
            EventPublisherPort auditEventPublisher) {
        return new DunningServiceImpl(
            dunningEventPublisher, dunningAttemptRepository, subscriptionRepository,
            subscriptionHistoryRepository, paymentMethodRepository, paymentRepository,
            ledgerRepository, billingProfileService, resolver, invoiceService,
            dunningPolicy, tenantScopePort, subscriptionEventPublisherPort, auditEventPublisher);
    }
}
