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
import com.company.bsmsvc.domain.model.payment.PaymentIntentResult;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import com.company.bsmsvc.domain.port.TenantScopePort;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.lenient;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceImplTest {

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

    @BeforeEach
    void setUp() {
        lenient().when(tenantScopeEnforcer.resolveEffectiveTenantId(any())).thenAnswer(inv -> inv.getArgument(0));
        tenantId = UUID.randomUUID();
        invoiceId = UUID.randomUUID();
        when(resolver.resolve(any())).thenReturn(gateway);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createCheckoutSession_returnsSessionResult() {
        setupMocks("cus_123");
        when(gateway.createCheckoutSession(any())).thenReturn(new CheckoutSessionResult("cs_test", "https://checkout.stripe.com/cs_test"));

        var result = paymentService.createCheckoutSession(tenantId, invoiceId, "https://ok", "https://cancel");

        assertThat(result.sessionId()).isEqualTo("cs_test");
        verify(paymentRepository).save(any());
    }

    @Test
    void createCheckoutSession_throwsWhenInvoiceNotOpen() {
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice(InvoiceStatus.PAID)));
        when(billingProfileService.getProfile(tenantId)).thenReturn(profile("cus_123"));

        assertThatThrownBy(() -> paymentService.createCheckoutSession(tenantId, invoiceId, "ok", "cancel"))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("not payable");
    }

    @Test
    void createCheckoutSession_throwsWhenNoCustomer() {
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice(InvoiceStatus.OPEN)));
        when(billingProfileService.getProfile(tenantId)).thenReturn(profile(null));

        assertThatThrownBy(() -> paymentService.createCheckoutSession(tenantId, invoiceId, "ok", "cancel"))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("No provider customer");
    }

    @Test
    void createPaymentIntent_returnsIntentResult() {
        setupMocks("cus_123");
        when(gateway.createPaymentIntent(any())).thenReturn(new PaymentIntentResult("pi_test", "pi_test_secret_xxx", "requires_payment_method"));

        var result = paymentService.createPaymentIntent(tenantId, invoiceId);

        assertThat(result.paymentIntentId()).isEqualTo("pi_test");
        verify(paymentRepository).save(any());
    }

    private void setupMocks(String externalCustomerId) {
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice(InvoiceStatus.OPEN)));
        when(billingProfileService.getProfile(tenantId)).thenReturn(profile(externalCustomerId));
    }

    private PlatformInvoice invoice(InvoiceStatus status) {
        return PlatformInvoice.builder()
            .id(invoiceId).tenantId(tenantId).subscriptionId(UUID.randomUUID())
            .invoiceNumber("INV-001").status(status)
            .amountDue(10000L).amountPaid(0L).currency("INR")
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
