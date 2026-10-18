package com.company.bsmsvc.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.domain.exception.PaymentGatewayException;
import com.company.bsmsvc.domain.model.payment.CancelSubscriptionCommand;
import com.company.bsmsvc.domain.model.payment.CreateSubscriptionCommand;
import com.company.bsmsvc.domain.model.payment.SubscriptionResult;
import com.company.bsmsvc.domain.model.payment.UpdateSubscriptionCommand;
import com.razorpay.RazorpayException;
import com.razorpay.Subscription;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RazorpayPaymentAdapterSubscriptionTest {

    @Mock private RazorpayClientWrapper razorpayClient;

    private RazorpayPaymentAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RazorpayPaymentAdapter(razorpayClient);
    }

    @Test
    void createSubscription_returnsResult() throws Exception {
        Subscription sub = new Subscription(new org.json.JSONObject("{\"id\":\"sub_rz_123\",\"status\":\"created\"}"));
        when(razorpayClient.createSubscription(any())).thenReturn(sub);

        SubscriptionResult result = adapter.createSubscription(
            new CreateSubscriptionCommand("cust_123", "plan_123", Map.of(), null, null));

        assertThat(result.externalSubscriptionId()).isEqualTo("sub_rz_123");
    }

    @Test
    void createSubscription_throwsPaymentGatewayException_onRazorpayError() throws Exception {
        when(razorpayClient.createSubscription(any())).thenThrow(new RazorpayException("API error"));

        assertThatThrownBy(() -> adapter.createSubscription(
            new CreateSubscriptionCommand("cust_123", "plan_123", Map.of(), null, null)))
            .isInstanceOf(PaymentGatewayException.class)
            .hasMessageContaining("Razorpay subscription creation failed");
    }

    @Test
    void createSubscription_throwsOnNullCommand() {
        assertThatThrownBy(() -> adapter.createSubscription(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createSubscription_throwsOnBlankPriceId() {
        assertThatThrownBy(() -> adapter.createSubscription(
            new CreateSubscriptionCommand("cust_123", "", Map.of(), null, null)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateSubscription_returnsResult() throws Exception {
        Subscription sub = new Subscription(new org.json.JSONObject("{\"id\":\"sub_rz_123\",\"status\":\"active\"}"));
        when(razorpayClient.updateSubscription(eq("sub_rz_123"), any())).thenReturn(sub);

        SubscriptionResult result = adapter.updateSubscription(
            new UpdateSubscriptionCommand("sub_rz_123", "plan_new"));

        assertThat(result.externalSubscriptionId()).isEqualTo("sub_rz_123");
    }

    @Test
    void cancelSubscription_immediately_callsClientWithFalse() throws Exception {
        adapter.cancelSubscription(new CancelSubscriptionCommand("sub_rz_123", true));
        verify(razorpayClient).cancelSubscription(eq("sub_rz_123"), eq(false));
    }

    @Test
    void cancelSubscription_atPeriodEnd_callsClientWithTrue() throws Exception {
        adapter.cancelSubscription(new CancelSubscriptionCommand("sub_rz_123", false));
        verify(razorpayClient).cancelSubscription(eq("sub_rz_123"), eq(true));
    }

    @Test
    void cancelSubscription_throwsPaymentGatewayException_onRazorpayError() throws Exception {
        org.mockito.Mockito.doThrow(new RazorpayException("error"))
            .when(razorpayClient).cancelSubscription(any(), any(boolean.class));

        assertThatThrownBy(() -> adapter.cancelSubscription(
            new CancelSubscriptionCommand("sub_rz_123", true)))
            .isInstanceOf(PaymentGatewayException.class);
    }
}
