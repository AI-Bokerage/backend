package com.company.bsmsvc.starter.config;

import com.company.bsmsvc.application.impl.InvoiceGenerationServiceImpl;
import com.company.bsmsvc.application.impl.InvoiceServiceImpl;
import com.company.bsmsvc.application.service.InvoiceGenerationService;
import com.company.bsmsvc.application.service.InvoiceNumberGenerator;
import com.company.bsmsvc.application.service.InvoiceService;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.port.EventPublisherPort;
import com.company.bsmsvc.domain.port.InvoiceEventPublisher;
import com.company.bsmsvc.domain.port.PlatformInvoiceRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionAddOnRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import com.company.bsmsvc.domain.port.TenantScopePort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Anchor port: {@link PlatformInvoiceRepositoryPort}. {@link InvoiceEventPublisher} is a
 * bsm-core port too — a consumer that doesn't care about invoice wire events can still supply
 * a no-op implementation; this auto-configuration doesn't invent one for them.
 */
@AutoConfiguration
@AutoConfigureAfter(TenantBillingProfileAutoConfiguration.class)
@ConditionalOnProperty(prefix = "bsm", name = "enabled", matchIfMissing = true)
@ConditionalOnBean({
    PlatformInvoiceRepositoryPort.class, InvoiceNumberGenerator.class, InvoiceEventPublisher.class,
    EventPublisherPort.class
})
public class InvoiceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public InvoiceGenerationService invoiceGenerationService(
            PlatformInvoiceRepositoryPort invoiceRepository,
            InvoiceNumberGenerator invoiceNumberGenerator,
            InvoiceEventPublisher invoiceEventPublisher,
            EventPublisherPort auditEventPublisher) {
        return new InvoiceGenerationServiceImpl(
            invoiceRepository, invoiceNumberGenerator, invoiceEventPublisher, auditEventPublisher);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({SubscriptionRepositoryPort.class, SubscriptionAddOnRepositoryPort.class,
        TenantBillingProfileService.class, TenantScopePort.class})
    public InvoiceService invoiceService(
            PlatformInvoiceRepositoryPort invoiceRepository,
            InvoiceGenerationService invoiceGenerationService,
            SubscriptionRepositoryPort subscriptionRepository,
            SubscriptionAddOnRepositoryPort subscriptionAddOnRepository,
            TenantBillingProfileService billingProfileService,
            InvoiceEventPublisher invoiceEventPublisher,
            TenantScopePort tenantScopePort,
            EventPublisherPort auditEventPublisher) {
        return new InvoiceServiceImpl(
            invoiceRepository, invoiceGenerationService, subscriptionRepository,
            subscriptionAddOnRepository, billingProfileService, invoiceEventPublisher,
            tenantScopePort, auditEventPublisher);
    }
}
