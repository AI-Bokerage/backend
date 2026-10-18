package com.company.bsmsvc.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.domain.exception.PaymentGatewayException;
import com.company.bsmsvc.domain.model.payment.CreateCustomerCommand;
import com.company.bsmsvc.domain.model.payment.CustomerResult;
import com.company.bsmsvc.domain.model.payment.ListPaymentMethodsCommand;
import com.company.bsmsvc.domain.model.payment.ListPaymentMethodsResult;
import com.razorpay.RazorpayException;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RazorpayPaymentAdapterTest {

    @Mock private RazorpayClientWrapper razorpayClient;

    private RazorpayPaymentAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RazorpayPaymentAdapter(razorpayClient);
    }

    @Test
    void createCustomer_returnsMappedResult() throws Exception {
        UUID tenantId = UUID.randomUUID();
        JSONObject response = new JSONObject();
        response.put("id", "cust_razorpay_123");
        when(razorpayClient.createCustomer(any())).thenReturn(response);

        CustomerResult result = adapter.createCustomer(new CreateCustomerCommand(tenantId, "Test", "test@test.com"));

        assertThat(result.externalCustomerId()).isEqualTo("cust_razorpay_123");
    }

    @Test
    void createCustomer_throwsPaymentGatewayException_onRazorpayError() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(razorpayClient.createCustomer(any())).thenThrow(new RazorpayException("API error"));

        assertThatThrownBy(() -> adapter.createCustomer(new CreateCustomerCommand(tenantId, "Test", "test@test.com")))
            .isInstanceOf(PaymentGatewayException.class)
            .hasMessageContaining("Razorpay customer creation failed");
    }

    @Test
    void createCustomer_throwsOnNullCommand() {
        assertThatThrownBy(() -> adapter.createCustomer(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listPaymentMethods_returnsEmpty() {
        ListPaymentMethodsResult result = adapter.listPaymentMethods(new ListPaymentMethodsCommand("cust_123"));
        assertThat(result.paymentMethods()).isEmpty();
    }

    @Test
    void attachPaymentMethod_throwsUnsupportedOperation() {
        assertThatThrownBy(() -> adapter.attachPaymentMethod(
            new com.company.bsmsvc.domain.model.payment.AttachPaymentMethodCommand("cust_123", "token")))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
