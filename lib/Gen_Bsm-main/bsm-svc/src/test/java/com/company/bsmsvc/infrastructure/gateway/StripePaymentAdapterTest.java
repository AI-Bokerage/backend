package com.company.bsmsvc.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.domain.exception.PaymentGatewayException;
import com.company.bsmsvc.domain.model.payment.AttachPaymentMethodCommand;
import com.company.bsmsvc.domain.model.payment.CreateCustomerCommand;
import com.company.bsmsvc.domain.model.payment.CustomerResult;
import com.company.bsmsvc.domain.model.payment.DetachPaymentMethodCommand;
import com.company.bsmsvc.domain.model.payment.ListPaymentMethodsCommand;
import com.company.bsmsvc.domain.model.payment.ListPaymentMethodsResult;
import com.stripe.exception.CardException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentMethod;
import com.stripe.model.PaymentMethodCollection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StripePaymentAdapterTest {

    @Mock private StripeClientWrapper stripeClient;

    private StripePaymentAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new StripePaymentAdapter(stripeClient);
    }

    @Test
    void createCustomer_returnsMappedResult() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Customer customer = new Customer();
        customer.setId("cus_test_123");
        when(stripeClient.createCustomer(any())).thenReturn(customer);

        CustomerResult result = adapter.createCustomer(new CreateCustomerCommand(tenantId, "Test User", "test@test.com"));

        assertThat(result.externalCustomerId()).isEqualTo("cus_test_123");
    }

    @Test
    void createCustomer_throwsPaymentGatewayException_onStripeError() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(stripeClient.createCustomer(any())).thenThrow(
            new CardException("Card declined", "req_123", "card_declined", "card_number", null, null, 402, null)
        );

        assertThatThrownBy(() -> adapter.createCustomer(new CreateCustomerCommand(tenantId, "Test", "test@test.com")))
            .isInstanceOf(PaymentGatewayException.class)
            .hasMessageContaining("Stripe customer creation failed");
    }

    @Test
    void createCustomer_throwsOnNullCommand() {
        assertThatThrownBy(() -> adapter.createCustomer(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void attachPaymentMethod_succeeds() throws Exception {
        PaymentMethod pm = new PaymentMethod();
        pm.setId("pm_test");
        when(stripeClient.attachPaymentMethod(any(), any())).thenReturn(pm);

        adapter.attachPaymentMethod(new AttachPaymentMethodCommand("cus_123", "pm_test"));
        // void — no exception = success
    }

    @Test
    void attachPaymentMethod_throwsPaymentGatewayException_onStripeError() throws Exception {
        when(stripeClient.attachPaymentMethod(any(), any())).thenThrow(
            new CardException("Card declined", "req_123", "card_declined", "card_number", null, null, 402, null)
        );

        assertThatThrownBy(() -> adapter.attachPaymentMethod(new AttachPaymentMethodCommand("cus_123", "pm_test")))
            .isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void detachPaymentMethod_succeeds() throws Exception {
        PaymentMethod pm = new PaymentMethod();
        when(stripeClient.detachPaymentMethod(any())).thenReturn(pm);

        adapter.detachPaymentMethod(new DetachPaymentMethodCommand("cus_123", "pm_test"));
    }

    @Test
    void listPaymentMethods_returnsEmptyWhenNoMethods() throws Exception {
        PaymentMethodCollection collection = new PaymentMethodCollection();
        collection.setData(List.of());
        when(stripeClient.listPaymentMethods(any())).thenReturn(collection);

        ListPaymentMethodsResult result = adapter.listPaymentMethods(new ListPaymentMethodsCommand("cus_123"));

        assertThat(result.paymentMethods()).isEmpty();
    }
}
