package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.application.service.PaymentGatewayResolver;
import com.company.bsmsvc.application.service.PaymentMethodService;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.enums.PaymentMethodStatus;
import com.company.bsmsvc.domain.enums.PaymentMethodType;
import com.company.bsmsvc.domain.event.PaymentMethodAddedEvent;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.exception.PaymentMethodNotFoundException;
import com.company.bsmsvc.domain.model.PaymentMethod;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import com.company.bsmsvc.domain.model.payment.AttachPaymentMethodCommand;
import com.company.bsmsvc.domain.model.payment.CreateCustomerCommand;
import com.company.bsmsvc.domain.model.payment.DetachPaymentMethodCommand;
import com.company.bsmsvc.domain.port.PaymentGatewayPort;
import com.company.bsmsvc.domain.port.PaymentMethodRepositoryPort;
import com.company.bsmsvc.domain.port.TenantScopePort;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentMethodServiceImpl implements PaymentMethodService {

    private final TenantBillingProfileService billingProfileService;
    private final PaymentMethodRepositoryPort paymentMethodRepository;
    private final PaymentGatewayResolver resolver;
    private final TenantScopePort tenantScopeEnforcer;

    public PaymentMethodServiceImpl(TenantBillingProfileService billingProfileService,
                                    PaymentMethodRepositoryPort paymentMethodRepository,
                                    PaymentGatewayResolver resolver,
                                    TenantScopePort tenantScopeEnforcer) {
        this.billingProfileService = billingProfileService;
        this.paymentMethodRepository = paymentMethodRepository;
        this.resolver = resolver;
        this.tenantScopeEnforcer = tenantScopeEnforcer;
    }

    @Override
    @Transactional
    public TenantBillingProfile createCustomerIfRequired(UUID tenantId, String name, String email) {
        tenantScopeEnforcer.assertTenantAccess(tenantId);
        TenantBillingProfile profile = billingProfileService.getProfile(tenantId);
        if (profile.getExternalCustomerId() != null && !profile.getExternalCustomerId().isBlank()) {
            return profile;
        }
        PaymentGatewayPort gateway = resolver.resolve(profile.getPaymentProvider());
        var result = gateway.createCustomer(new CreateCustomerCommand(tenantId, name, email));
        return billingProfileService.assignExternalCustomerId(tenantId, result.externalCustomerId());
    }

    /**
     * Ensures a provider customer exists for this billing profile within the CURRENT transaction.
     * If externalCustomerId is already set, returns it. Otherwise creates the customer immediately.
     */
    private String ensureCustomerExists(TenantBillingProfile profile, UUID tenantId) {
        String stored = profile.getExternalCustomerId();
        if (stored != null && !stored.isBlank() && isValidProviderId(stored, profile.getPaymentProvider())) {
            return stored;
        }
        // Customer not yet created (or stored value is invalid) — create now inside this transaction
        PaymentGatewayPort gateway = resolver.resolve(profile.getPaymentProvider());
        var result = gateway.createCustomer(new CreateCustomerCommand(tenantId, null, null));
        billingProfileService.assignExternalCustomerId(tenantId, result.externalCustomerId());
        return result.externalCustomerId();
    }

    private boolean isValidProviderId(String id, com.company.bsmsvc.domain.enums.PaymentProvider provider) {
        return switch (provider) {
            case STRIPE -> id.startsWith("cus_");
            case RAZORPAY -> id.startsWith("cust_");
        };
    }

    @Override
    @Transactional
    public PaymentMethod addPaymentMethod(UUID tenantId, String paymentMethodToken, PaymentMethodType type,
                                          String brand, String lastFour, Integer expMonth, Integer expYear,
                                          boolean makeDefault) {
        tenantScopeEnforcer.assertTenantAccess(tenantId);
        // Duplicate check before any provider call
        if (paymentMethodRepository.existsByTenantIdAndExternalPaymentMethodId(tenantId, paymentMethodToken)) {
            throw new BusinessRuleViolationException(
                "Payment method already added for tenant: " + tenantId);
        }

        TenantBillingProfile profile = billingProfileService.getProfile(tenantId);
        PaymentGatewayPort gateway = resolver.resolve(profile.getPaymentProvider());

        String customerId = ensureCustomerExists(profile, tenantId);
        gateway.attachPaymentMethod(new AttachPaymentMethodCommand(customerId, paymentMethodToken));

        if (makeDefault) {
            paymentMethodRepository.unsetDefaultForTenant(tenantId);
        }

        PaymentMethod pm = PaymentMethod.builder()
            .id(UUID.randomUUID())
            .tenantId(tenantId)
            .paymentProvider(profile.getPaymentProvider())
            .externalPaymentMethodId(paymentMethodToken)
            .type(type)
            .brand(brand)
            .lastFour(lastFour)
            .expMonth(expMonth)
            .expYear(expYear)
            .isDefault(makeDefault)
            .status(PaymentMethodStatus.ACTIVE)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        pm.registerEvent(new PaymentMethodAddedEvent(pm.getId(), tenantId, profile.getPaymentProvider(), paymentMethodToken, Instant.now()));
        return paymentMethodRepository.save(pm);
    }

    @Override
    public List<PaymentMethod> listPaymentMethods(UUID tenantId) {
        tenantScopeEnforcer.assertTenantAccess(tenantId);
        return paymentMethodRepository.findByTenantId(tenantId);
    }

    @Override
    @Transactional
    public void removePaymentMethod(UUID id) {
        // id-only entry point: tenantId derived from the entity itself
        PaymentMethod pm = paymentMethodRepository.findById(id)
            .orElseThrow(() -> new PaymentMethodNotFoundException("Payment method not found: " + id));
        TenantBillingProfile profile = billingProfileService.getProfile(pm.getTenantId());
        PaymentGatewayPort gateway = resolver.resolve(profile.getPaymentProvider());
        gateway.detachPaymentMethod(new DetachPaymentMethodCommand(profile.getExternalCustomerId(), pm.getExternalPaymentMethodId()));
        pm.markRemoved();
        paymentMethodRepository.save(pm);
    }

    @Override
    @Transactional
    public PaymentMethod setDefaultPaymentMethod(UUID id) {
        // id-only entry point: tenantId derived from the entity itself
        PaymentMethod pm = paymentMethodRepository.findById(id)
            .orElseThrow(() -> new PaymentMethodNotFoundException("Payment method not found: " + id));
        tenantScopeEnforcer.assertTenantAccess(pm.getTenantId());
        if (pm.getStatus() == PaymentMethodStatus.DETACHED) {
            throw new BusinessRuleViolationException("Payment method is detached and cannot be set as default");
        }
        paymentMethodRepository.unsetDefaultForTenant(pm.getTenantId());
        pm.setAsDefault();
        return paymentMethodRepository.save(pm);
    }
}
