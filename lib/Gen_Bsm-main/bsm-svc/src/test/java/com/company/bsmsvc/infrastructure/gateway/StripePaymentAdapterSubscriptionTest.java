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
import com.stripe.exception.CardException;
import com.stripe.model.Subscription;
import com.stripe.param.SubscriptionCancelParams;
import com.stripe.param.SubscriptionUpdateParams;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StripePaymentAdapterSubscriptionTest {

    @Mock private StripeClientWrapper stripeClient;

    private StripePaymentAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new StripePaymentAdapter(stripeClient);
    }

    @Test
    void createSubscription_returnsResult() throws Exception {
        Subscription sub = new Subscription();
        sub.setId("sub_test_123");
        sub.setStatus("active");
        when(stripeClient.createSubscription(any())).thenReturn(sub);

        SubscriptionResult result = adapter.createSubscription(
            new CreateSubscriptionCommand("cus_123", "price_123", Map.of(), null, null));

        assertThat(result.externalSubscriptionId()).isEqualTo("sub_test_123");
        assertThat(result.status()).isEqualTo("active");
    }

    @Test
    void createSubscription_throwsPaymentGatewayException_onStripeError() throws Exception {
        when(stripeClient.createSubscription(any())).thenThrow(
            new CardException("Card error", "req_1", "card_declined", "card", null, null, 402, null)
        );

        assertThatThrownBy(() -> adapter.createSubscription(
            new CreateSubscriptionCommand("cus_123", "price_123", Map.of(), null, null)))
            .isInstanceOf(PaymentGatewayException.class)
            .hasMessageContaining("Stripe subscription creation failed");
    }

    @Test
    void createSubscription_throwsOnNullCommand() {
        assertThatThrownBy(() -> adapter.createSubscription(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createSubscription_throwsOnBlankPriceId() {
        assertThatThrownBy(() -> adapter.createSubscription(
            new CreateSubscriptionCommand("cus_123", "", Map.of(), null, null)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateSubscription_returnsUpdatedResult() throws Exception {
        String subId = "sub_test_123";
        String oldPriceId = "price_old";
        String newPriceId = "price_new";

        // Build a subscription with one item so the adapter can get the existing item ID
        com.stripe.model.SubscriptionItem item = new com.stripe.model.SubscriptionItem();
        item.setId("si_existing");
        com.stripe.model.Price price = new com.stripe.model.Price();
        price.setId(oldPriceId);
        item.setPrice(price);
        com.stripe.model.SubscriptionItemCollection items = new com.stripe.model.SubscriptionItemCollection();
        items.setData(java.util.List.of(item));

        Subscription current = new Subscription();
        current.setId(subId);
        current.setStatus("active");
        current.setItems(items);

        Subscription updated = new Subscription();
        updated.setId(subId);
        updated.setStatus("active");

        when(stripeClient.retrieveSubscription(subId)).thenReturn(current);
        when(stripeClient.updateSubscription(eq(subId), any())).thenReturn(updated);

        SubscriptionResult result = adapter.updateSubscription(new UpdateSubscriptionCommand(subId, newPriceId));

        assertThat(result.externalSubscriptionId()).isEqualTo(subId);
        verify(stripeClient).updateSubscription(eq(subId), any(SubscriptionUpdateParams.class));
    }

    @Test
    void cancelSubscription_immediate_callsCancelEndpoint() throws Exception {
        Subscription sub = new Subscription();
        sub.setId("sub_test_123");
        when(stripeClient.cancelSubscription(eq("sub_test_123"), any())).thenReturn(sub);

        adapter.cancelSubscription(new CancelSubscriptionCommand("sub_test_123", true));

        verify(stripeClient).cancelSubscription(eq("sub_test_123"), any(SubscriptionCancelParams.class));
    }

    @Test
    void cancelSubscription_atPeriodEnd_callsUpdateEndpoint() throws Exception {
        Subscription sub = new Subscription();
        sub.setId("sub_test_123");
        when(stripeClient.updateSubscription(eq("sub_test_123"), any())).thenReturn(sub);

        adapter.cancelSubscription(new CancelSubscriptionCommand("sub_test_123", false));

        verify(stripeClient).updateSubscription(eq("sub_test_123"), any(SubscriptionUpdateParams.class));
    }

    @Test
    void cancelSubscription_throwsPaymentGatewayException_onStripeError() throws Exception {
        when(stripeClient.cancelSubscription(any(), any())).thenThrow(
            new CardException("error", "req_1", "code", "param", null, null, 402, null)
        );

        assertThatThrownBy(() -> adapter.cancelSubscription(new CancelSubscriptionCommand("sub_123", true)))
            .isInstanceOf(PaymentGatewayException.class);
    }
}
