package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.application.service.PaymentGatewayResolver;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.enums.InvoiceStatus;
import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import com.company.bsmsvc.domain.model.payment.CheckoutSessionResult;
import com.company.bsmsvc.domain.model.payment.CreateCheckoutSessionCommand;
import com.company.bsmsvc.domain.port.PaymentGatewayPort;
import com.company.bsmsvc.domain.port.PaymentRepositoryPort;
import com.company.bsmsvc.domain.port.PlatformInvoiceRepositoryPort;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import com.company.bsmsvc.domain.port.TenantScopePort;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.lenient;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckoutSessionAmountTest {

    @Mock private TenantBillingProfileService billingProfileService;
    @Mock private PaymentGatewayResolver resolver;
    @Mock private PaymentRepositoryPort paymentRepository;
    @Mock private PlatformInvoiceRepositoryPort invoiceRepository;
    @Mock private PaymentGatewayPort gateway;
    @Mock private TenantScopePort tenantScopeEnforcer;
    @Mock private com.company.bsmsvc.domain.port.EventPublisherPort auditEventPublisher;

    @InjectMocks private PaymentServiceImpl paymentService;

    private UUID tenantId;
    private UUID invoiceId;
    private UUID subscriptionId;

    @BeforeEach
    void setUp() {
        lenient().when(tenantScopeEnforcer.resolveEffectiveTenantId(any())).thenAnswer(inv -> inv.getArgument(0));
        tenantId = UUID.randomUUID();
        invoiceId = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();
        when(resolver.resolve(any())).thenReturn(gateway);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createCheckoutSession_passesCorrectAmountAndCurrency() {
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice(15000L, "INR")));
        when(billingProfileService.getProfile(tenantId)).thenReturn(profile("cus_123"));
        when(gateway.createCheckoutSession(any())).thenReturn(new CheckoutSessionResult("cs_test", "https://checkout"));

        paymentService.createCheckoutSession(tenantId, invoiceId, "https://ok", "https://cancel");

        ArgumentCaptor<CreateCheckoutSessionCommand> captor = ArgumentCaptor.forClass(CreateCheckoutSessionCommand.class);
        verify(gateway).createCheckoutSession(captor.capture());
        assertThat(captor.getValue().amountMinor()).isEqualTo(15000L);
        assertThat(captor.getValue().currency()).isEqualTo("INR");
        assertThat(captor.getValue().externalCustomerId()).isEqualTo("cus_123");
    }

    @Test
    void createCheckoutSession_includesInvoiceIdInMetadata() {
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice(15000L, "INR")));
        when(billingProfileService.getProfile(tenantId)).thenReturn(profile("cus_123"));
        when(gateway.createCheckoutSession(any())).thenReturn(new CheckoutSessionResult("cs_test", "https://checkout"));

        paymentService.createCheckoutSession(tenantId, invoiceId, "https://ok", "https://cancel");

        ArgumentCaptor<CreateCheckoutSessionCommand> captor = ArgumentCaptor.forClass(CreateCheckoutSessionCommand.class);
        verify(gateway).createCheckoutSession(captor.capture());
        assertThat(captor.getValue().metadata()).containsKey("invoiceId");
        assertThat(captor.getValue().metadata().get("invoiceId")).isEqualTo(invoiceId.toString());
        assertThat(captor.getValue().metadata()).containsKey("tenantId");
        assertThat(captor.getValue().metadata()).containsKey("subscriptionId");
    }

    @Test
    void createCheckoutSession_throwsWhenAmountIsZero() {
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice(0L, "INR")));
        when(billingProfileService.getProfile(tenantId)).thenReturn(profile("cus_123"));

        assertThatThrownBy(() -> paymentService.createCheckoutSession(tenantId, invoiceId, "ok", "cancel"))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("amount must be greater than zero");
    }

    @Test
    void createCheckoutSession_includesDescriptionWithInvoiceNumber() {
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice(10000L, "USD")));
        when(billingProfileService.getProfile(tenantId)).thenReturn(profile("cus_123"));
        when(gateway.createCheckoutSession(any())).thenReturn(new CheckoutSessionResult("cs_x", "https://checkout"));

        paymentService.createCheckoutSession(tenantId, invoiceId, "ok", "cancel");

        ArgumentCaptor<CreateCheckoutSessionCommand> captor = ArgumentCaptor.forClass(CreateCheckoutSessionCommand.class);
        verify(gateway).createCheckoutSession(captor.capture());
        assertThat(captor.getValue().description()).contains("INV-001");
    }

    private PlatformInvoice invoice(long amount, String currency) {
        return PlatformInvoice.builder()
            .id(invoiceId).tenantId(tenantId).subscriptionId(subscriptionId)
            .invoiceNumber("INV-001").status(InvoiceStatus.OPEN)
            .amountDue(amount).amountPaid(0L).currency(currency)
            .periodStart(Instant.now()).periodEnd(Instant.now().plusSeconds(86400))
            .dueDate(LocalDate.now().plusDays(30))
            .lineItems(new ArrayList<>()).domainEvents(new ArrayList<>()).build();
    }

    private TenantBillingProfile profile(String customerId) {
        return TenantBillingProfile.builder()
            .id(UUID.randomUUID()).tenantId(tenantId)
            .paymentProvider(PaymentProvider.STRIPE).externalCustomerId(customerId)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .domainEvents(new ArrayList<>()).build();
    }
}
