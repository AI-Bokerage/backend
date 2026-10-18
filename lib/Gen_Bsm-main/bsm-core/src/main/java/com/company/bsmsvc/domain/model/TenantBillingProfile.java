package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.event.CustomerCreatedEvent;
import com.company.bsmsvc.domain.event.TenantBillingCurrencyChangedEvent;
import com.company.bsmsvc.domain.event.TenantBillingProviderChangedEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TenantBillingProfile {

    private UUID id;
    private UUID tenantId;
    private PaymentProvider paymentProvider;
    private String externalCustomerId;
    private String currency;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    @Builder.Default
    private List<Object> domainEvents = new ArrayList<>();

    public void assignExternalCustomerId(String externalCustomerId) {
        this.externalCustomerId = externalCustomerId;
        this.updatedAt = Instant.now();
        registerEvent(new CustomerCreatedEvent(this.id, this.tenantId, this.paymentProvider, externalCustomerId, Instant.now()));
    }

    public void updateCurrency(String newCurrency) {
        if (newCurrency.equals(this.currency)) return;
        String old = this.currency;
        this.currency = newCurrency;
        this.updatedAt = Instant.now();
        registerEvent(new TenantBillingCurrencyChangedEvent(this.id, this.tenantId, old, newCurrency, Instant.now()));
    }

    public void updateProvider(PaymentProvider newProvider) {
        if (this.paymentProvider == newProvider) return;
        PaymentProvider old = this.paymentProvider;
        this.paymentProvider = newProvider;
        this.updatedAt = Instant.now();
        registerEvent(new TenantBillingProviderChangedEvent(this.id, this.tenantId, old, newProvider, Instant.now()));
    }

    public void registerEvent(Object event) {
        if (this.domainEvents == null) this.domainEvents = new ArrayList<>();
        this.domainEvents.add(event);
    }

    public List<Object> pullDomainEvents() {
        if (this.domainEvents == null || this.domainEvents.isEmpty()) return Collections.emptyList();
        List<Object> snapshot = new ArrayList<>(this.domainEvents);
        this.domainEvents.clear();
        return snapshot;
    }

    public void clearDomainEvents() {
        if (this.domainEvents != null) this.domainEvents.clear();
    }
}
