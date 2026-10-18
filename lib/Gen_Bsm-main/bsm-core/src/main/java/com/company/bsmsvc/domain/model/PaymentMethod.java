package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.PaymentMethodStatus;
import com.company.bsmsvc.domain.enums.PaymentMethodType;
import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.event.PaymentMethodDefaultChangedEvent;
import com.company.bsmsvc.domain.event.PaymentMethodRemovedEvent;
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
public class PaymentMethod {

    private UUID id;
    private UUID tenantId;
    private PaymentProvider paymentProvider;
    private String externalPaymentMethodId;
    private PaymentMethodType type;
    private String brand;
    private String lastFour;
    private Integer expMonth;
    private Integer expYear;
    private boolean isDefault;
    private PaymentMethodStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    @Builder.Default
    private List<Object> domainEvents = new ArrayList<>();

    public void markRemoved() {
        this.status = PaymentMethodStatus.DETACHED;
        this.updatedAt = Instant.now();
        registerEvent(new PaymentMethodRemovedEvent(this.id, this.tenantId, this.externalPaymentMethodId, Instant.now()));
    }

    public void setAsDefault() {
        this.isDefault = true;
        this.updatedAt = Instant.now();
        registerEvent(new PaymentMethodDefaultChangedEvent(this.id, this.tenantId, Instant.now()));
    }

    public void unsetAsDefault() {
        this.isDefault = false;
        this.updatedAt = Instant.now();
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
