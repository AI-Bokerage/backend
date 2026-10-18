package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.event.TenantBillingCurrencyChangedEvent;
import com.company.bsmsvc.domain.event.TenantBillingProfileCreatedEvent;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.exception.TenantBillingProfileNotFoundException;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import com.company.bsmsvc.domain.port.TenantBillingProfileRepositoryPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import com.company.bsmsvc.domain.port.TenantScopePort;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class TenantBillingProfileServiceImplTest {

    @Mock private TenantBillingProfileRepositoryPort repository;
    @Mock private TenantScopePort tenantScopeEnforcer;

    @InjectMocks private TenantBillingProfileServiceImpl service;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        lenient().when(tenantScopeEnforcer.resolveEffectiveTenantId(any())).thenAnswer(inv -> inv.getArgument(0));
        tenantId = UUID.randomUUID();
    }

    @Test
    void createProfile_savesProfileWithCorrectFields() {
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        TenantBillingProfile saved = profile(tenantId, PaymentProvider.STRIPE);
        when(repository.save(any())).thenReturn(saved);

        TenantBillingProfile result = service.createProfile(tenantId, PaymentProvider.STRIPE, "cus_test", "USD");

        assertThat(result.getTenantId()).isEqualTo(tenantId);
        assertThat(result.getPaymentProvider()).isEqualTo(PaymentProvider.STRIPE);
        verify(repository).save(any(TenantBillingProfile.class));
    }

    @Test
    void createProfile_storesCurrencyFromRequest() {
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TenantBillingProfile result = service.createProfile(tenantId, PaymentProvider.STRIPE, null, "EUR");

        assertThat(result.getCurrency()).isEqualTo("EUR");
    }

    @Test
    void createProfile_registersCreatedEvent() {
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TenantBillingProfile result = service.createProfile(tenantId, PaymentProvider.STRIPE, null, "INR");

        assertThat(result.pullDomainEvents())
            .hasSize(1)
            .first().isInstanceOf(TenantBillingProfileCreatedEvent.class);
    }

    @Test
    void createProfile_throwsWhenProfileAlreadyExists() {
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(profile(tenantId, PaymentProvider.STRIPE)));

        assertThatThrownBy(() -> service.createProfile(tenantId, PaymentProvider.RAZORPAY, null, "USD"))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void updateCurrency_changesCurrencyAndSaves() {
        TenantBillingProfile existing = profile(tenantId, PaymentProvider.STRIPE);
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenAnswer(inv -> inv.getArgument(0));

        TenantBillingProfile result = service.updateCurrency(tenantId, "EUR");

        assertThat(result.getCurrency()).isEqualTo("EUR");
        verify(repository).save(existing);
    }

    @Test
    void updateCurrency_isIdempotentWhenSameCurrency() {
        TenantBillingProfile existing = profile(tenantId, PaymentProvider.STRIPE);
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenAnswer(inv -> inv.getArgument(0));

        TenantBillingProfile result = service.updateCurrency(tenantId, "INR");

        assertThat(result.getCurrency()).isEqualTo("INR");
        assertThat(result.pullDomainEvents()).isEmpty();
    }

    @Test
    void updateCurrency_registersChangedEvent() {
        TenantBillingProfile existing = profile(tenantId, PaymentProvider.STRIPE);
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenAnswer(inv -> inv.getArgument(0));

        TenantBillingProfile result = service.updateCurrency(tenantId, "USD");

        assertThat(result.pullDomainEvents())
            .hasSize(1)
            .first().isInstanceOf(TenantBillingCurrencyChangedEvent.class);
    }

    @Test
    void updateCurrency_throwsWhenProfileNotFound() {
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCurrency(tenantId, "EUR"))
            .isInstanceOf(TenantBillingProfileNotFoundException.class);
    }

    @Test
    void getProfile_returnsWhenFound() {
        TenantBillingProfile p = profile(tenantId, PaymentProvider.RAZORPAY);
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(p));

        TenantBillingProfile result = service.getProfile(tenantId);

        assertThat(result.getTenantId()).isEqualTo(tenantId);
        assertThat(result.getPaymentProvider()).isEqualTo(PaymentProvider.RAZORPAY);
    }

    @Test
    void getProfile_throwsWhenNotFound() {
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile(tenantId))
            .isInstanceOf(TenantBillingProfileNotFoundException.class)
            .hasMessageContaining(tenantId.toString());
    }

    @Test
    void updateProvider_changesProviderAndSaves() {
        TenantBillingProfile existing = profile(tenantId, PaymentProvider.STRIPE);
        TenantBillingProfile updated = profile(tenantId, PaymentProvider.RAZORPAY);
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(updated);

        TenantBillingProfile result = service.updateProvider(tenantId, PaymentProvider.RAZORPAY);

        assertThat(result.getPaymentProvider()).isEqualTo(PaymentProvider.RAZORPAY);
        verify(repository).save(existing);
    }

    @Test
    void updateProvider_throwsWhenProfileNotFound() {
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateProvider(tenantId, PaymentProvider.STRIPE))
            .isInstanceOf(TenantBillingProfileNotFoundException.class);
    }

    @Test
    void updateProvider_sameProvider_isIdempotent() {
        TenantBillingProfile existing = profile(tenantId, PaymentProvider.STRIPE);
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        TenantBillingProfile result = service.updateProvider(tenantId, PaymentProvider.STRIPE);

        assertThat(result.getPaymentProvider()).isEqualTo(PaymentProvider.STRIPE);
        assertThat(existing.pullDomainEvents()).isEmpty();
    }

    private TenantBillingProfile profile(UUID tenantId, PaymentProvider provider) {
        return TenantBillingProfile.builder()
            .id(UUID.randomUUID()).tenantId(tenantId)
            .paymentProvider(provider).currency("INR")
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .domainEvents(new ArrayList<>()).build();
    }
}
