package com.company.bsmsvc.starter.config;

import com.company.bsmsvc.application.impl.InvoiceRenewalServiceImpl;
import com.company.bsmsvc.application.service.InvoiceRenewalService;
import com.company.bsmsvc.application.service.InvoiceService;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.port.PlatformInvoiceRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionEventPublisherPort;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Anchor: {@link InvoiceService} + {@link SubscriptionRepositoryPort} — renewal is meaningless
 * without both the subscription and invoice aggregates already wired.
 */
@AutoConfiguration
@AutoConfigureAfter({InvoiceAutoConfiguration.class, SubscriptionAutoConfiguration.class})
@ConditionalOnProperty(prefix = "bsm", name = "enabled", matchIfMissing = true)
@ConditionalOnBean({
    SubscriptionRepositoryPort.class, InvoiceService.class, PlatformInvoiceRepositoryPort.class,
    TenantBillingProfileService.class, SubscriptionEventPublisherPort.class
})
public class InvoiceRenewalAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public InvoiceRenewalService invoiceRenewalService(
            SubscriptionRepositoryPort subscriptionRepository,
            InvoiceService invoiceService,
            PlatformInvoiceRepositoryPort invoiceRepository,
            TenantBillingProfileService billingProfileService,
            SubscriptionEventPublisherPort subscriptionEventPublisherPort) {
        return new InvoiceRenewalServiceImpl(
            subscriptionRepository, invoiceService, invoiceRepository,
            billingProfileService, subscriptionEventPublisherPort);
    }
}
