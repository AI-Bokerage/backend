package com.company.bsmsvc.starter.config;

import com.company.bsmsvc.application.impl.PaymentMethodServiceImpl;
import com.company.bsmsvc.application.impl.PaymentServiceImpl;
import com.company.bsmsvc.application.service.PaymentGatewayResolver;
import com.company.bsmsvc.application.service.PaymentMethodService;
import com.company.bsmsvc.application.service.PaymentService;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.port.EventPublisherPort;
import com.company.bsmsvc.domain.port.PaymentMethodRepositoryPort;
import com.company.bsmsvc.domain.port.PaymentRepositoryPort;
import com.company.bsmsvc.domain.port.PlatformInvoiceRepositoryPort;
import com.company.bsmsvc.domain.port.TenantScopePort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Anchor port: {@link PaymentGatewayResolver} — a consumer with no payment gateway has no use
 * for either service here. Also requires {@link PaymentMethodRepositoryPort},
 * {@link PaymentRepositoryPort}, {@link PlatformInvoiceRepositoryPort}, {@link TenantScopePort},
 * {@link EventPublisherPort}, and {@link TenantBillingProfileService} (registered by
 * {@link TenantBillingProfileAutoConfiguration}).
 */
@AutoConfiguration
@AutoConfigureAfter(TenantBillingProfileAutoConfiguration.class)
@ConditionalOnProperty(prefix = "bsm", name = "enabled", matchIfMissing = true)
@ConditionalOnBean({
    PaymentGatewayResolver.class, PaymentMethodRepositoryPort.class, PaymentRepositoryPort.class,
    PlatformInvoiceRepositoryPort.class, TenantScopePort.class, EventPublisherPort.class,
    TenantBillingProfileService.class
})
public class PaymentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PaymentMethodService paymentMethodService(
            TenantBillingProfileService billingProfileService,
            PaymentMethodRepositoryPort paymentMethodRepository,
            PaymentGatewayResolver resolver,
            TenantScopePort tenantScopePort) {
        return new PaymentMethodServiceImpl(
            billingProfileService, paymentMethodRepository, resolver, tenantScopePort);
    }

    @Bean
    @ConditionalOnMissingBean
    public PaymentService paymentService(
            TenantBillingProfileService billingProfileService,
            PaymentGatewayResolver resolver,
            PaymentRepositoryPort paymentRepository,
            PlatformInvoiceRepositoryPort invoiceRepository,
            TenantScopePort tenantScopePort,
            EventPublisherPort auditEventPublisher) {
        return new PaymentServiceImpl(
            billingProfileService, resolver, paymentRepository, invoiceRepository,
            tenantScopePort, auditEventPublisher);
    }
}
