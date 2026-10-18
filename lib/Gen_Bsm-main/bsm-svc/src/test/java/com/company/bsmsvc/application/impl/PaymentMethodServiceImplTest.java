package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.application.service.PaymentGatewayResolver;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.enums.PaymentMethodStatus;
import com.company.bsmsvc.domain.enums.PaymentMethodType;
import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.exception.PaymentMethodNotFoundException;
import com.company.bsmsvc.domain.model.PaymentMethod;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import com.company.bsmsvc.domain.model.payment.CustomerResult;
import com.company.bsmsvc.domain.port.PaymentGatewayPort;
import com.company.bsmsvc.domain.port.PaymentMethodRepositoryPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentMethodServiceImplTest {

    @Mock private TenantBillingProfileService billingProfileService;
    @Mock private PaymentMethodRepositoryPort paymentMethodRepository;
    @Mock private PaymentGatewayResolver resolver;
    @Mock private PaymentGatewayPort gateway;
    @Mock private TenantScopePort tenantScopeEnforcer;

    @InjectMocks private PaymentMethodServiceImpl service;

    private UUID tenantId;
    private UUID pmId;

    @BeforeEach
    void setUp() {
        lenient().when(tenantScopeEnforcer.resolveEffectiveTenantId(any())).thenAnswer(inv -> inv.getArgument(0));
        tenantId = UUID.randomUUID();
        pmId = UUID.randomUUID();
        when(resolver.resolve(any())).thenReturn(gateway);
    }

    @Test
    void createCustomerIfRequired_whenNoCustomer_createsAndAssigns() {
        TenantBillingProfile profile = profile(tenantId, null);
        TenantBillingProfile updated = profile(tenantId, "cus_test");
        when(billingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(gateway.createCustomer(any())).thenReturn(new CustomerResult("cus_test"));
        when(billingProfileService.assignExternalCustomerId(tenantId, "cus_test")).thenReturn(updated);

        TenantBillingProfile result = service.createCustomerIfRequired(tenantId, "Test", "test@test.com");

        assertThat(result.getExternalCustomerId()).isEqualTo("cus_test");
        verify(gateway).createCustomer(any());
        verify(billingProfileService).assignExternalCustomerId(tenantId, "cus_test");
    }

    @Test
    void createCustomerIfRequired_whenCustomerExists_isIdempotent() {
        TenantBillingProfile profile = profile(tenantId, "cus_existing");
        when(billingProfileService.getProfile(tenantId)).thenReturn(profile);

        TenantBillingProfile result = service.createCustomerIfRequired(tenantId, "Test", "test@test.com");

        assertThat(result.getExternalCustomerId()).isEqualTo("cus_existing");
        verify(gateway, never()).createCustomer(any());
    }

    @Test
    void addPaymentMethod_savesAndRegistersEvent() {
        TenantBillingProfile profile = profile(tenantId, "cus_123");
        when(billingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(paymentMethodRepository.existsByTenantIdAndExternalPaymentMethodId(tenantId, "pm_test")).thenReturn(false);
        when(paymentMethodRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentMethod result = service.addPaymentMethod(tenantId, "pm_test", PaymentMethodType.CARD, "visa", "4242", 12, 2028, false);

        assertThat(result.getTenantId()).isEqualTo(tenantId);
        assertThat(result.getExternalPaymentMethodId()).isEqualTo("pm_test");
        assertThat(result.getStatus()).isEqualTo(PaymentMethodStatus.ACTIVE);
        verify(gateway).attachPaymentMethod(any());
    }

    @Test
    void addPaymentMethod_throwsWhenDuplicate() {
        TenantBillingProfile profile = profile(tenantId, "cus_123");
        when(billingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(paymentMethodRepository.existsByTenantIdAndExternalPaymentMethodId(tenantId, "pm_dup")).thenReturn(true);

        assertThatThrownBy(() -> service.addPaymentMethod(tenantId, "pm_dup", PaymentMethodType.CARD, null, null, null, null, false))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("already added");
    }

    @Test
    void addPaymentMethod_autoCreatesCustomer_whenNoneExists() {
        TenantBillingProfile profile = profile(tenantId, null);
        when(billingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(paymentMethodRepository.existsByTenantIdAndExternalPaymentMethodId(any(), any())).thenReturn(false);
        when(gateway.createCustomer(any())).thenReturn(new com.company.bsmsvc.domain.model.payment.CustomerResult("cus_new"));
        when(billingProfileService.assignExternalCustomerId(tenantId, "cus_new")).thenReturn(profile(tenantId, "cus_new"));
        when(paymentMethodRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentMethod result = service.addPaymentMethod(tenantId, "pm_test", PaymentMethodType.CARD, null, null, null, null, false);

        assertThat(result).isNotNull();
        verify(gateway).createCustomer(any());
        verify(gateway).attachPaymentMethod(any());
    }

    @Test
    void listPaymentMethods_delegatesToRepository() {
        PaymentMethod pm = activePaymentMethod(pmId, tenantId);
        when(paymentMethodRepository.findByTenantId(tenantId)).thenReturn(List.of(pm));

        List<PaymentMethod> result = service.listPaymentMethods(tenantId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(pmId);
    }

    @Test
    void removePaymentMethod_detachesAndMarksRemoved() {
        PaymentMethod pm = activePaymentMethod(pmId, tenantId);
        TenantBillingProfile profile = profile(tenantId, "cus_123");
        when(paymentMethodRepository.findById(pmId)).thenReturn(Optional.of(pm));
        when(billingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(paymentMethodRepository.save(pm)).thenReturn(pm);

        service.removePaymentMethod(pmId);

        assertThat(pm.getStatus()).isEqualTo(PaymentMethodStatus.DETACHED);
        verify(gateway).detachPaymentMethod(any());
        verify(paymentMethodRepository).save(pm);
    }

    @Test
    void removePaymentMethod_throwsWhenNotFound() {
        when(paymentMethodRepository.findById(pmId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removePaymentMethod(pmId))
            .isInstanceOf(PaymentMethodNotFoundException.class);
    }

    @Test
    void setDefaultPaymentMethod_unsetsOthersAndSets() {
        PaymentMethod pm = activePaymentMethod(pmId, tenantId);
        when(paymentMethodRepository.findById(pmId)).thenReturn(Optional.of(pm));
        when(paymentMethodRepository.save(pm)).thenReturn(pm);

        PaymentMethod result = service.setDefaultPaymentMethod(pmId);

        assertThat(result.isDefault()).isTrue();
        verify(paymentMethodRepository).unsetDefaultForTenant(tenantId);
    }

    @Test
    void setDefaultPaymentMethod_throwsWhenDetached() {
        PaymentMethod pm = PaymentMethod.builder()
            .id(pmId).tenantId(tenantId).status(PaymentMethodStatus.DETACHED)
            .domainEvents(new ArrayList<>()).build();
        when(paymentMethodRepository.findById(pmId)).thenReturn(Optional.of(pm));

        assertThatThrownBy(() -> service.setDefaultPaymentMethod(pmId))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("detached");
    }

    private TenantBillingProfile profile(UUID tenantId, String externalCustomerId) {
        return TenantBillingProfile.builder()
            .id(UUID.randomUUID()).tenantId(tenantId)
            .paymentProvider(PaymentProvider.STRIPE)
            .externalCustomerId(externalCustomerId)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .domainEvents(new ArrayList<>()).build();
    }

    private PaymentMethod activePaymentMethod(UUID id, UUID tenantId) {
        return PaymentMethod.builder()
            .id(id).tenantId(tenantId)
            .paymentProvider(PaymentProvider.STRIPE)
            .externalPaymentMethodId("pm_test")
            .type(PaymentMethodType.CARD).brand("visa").lastFour("4242")
            .expMonth(12).expYear(2028).isDefault(false)
            .status(PaymentMethodStatus.ACTIVE)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .domainEvents(new ArrayList<>()).build();
    }
}
