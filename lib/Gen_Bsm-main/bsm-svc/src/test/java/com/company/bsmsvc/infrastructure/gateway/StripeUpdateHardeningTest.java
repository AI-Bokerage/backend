package com.company.bsmsvc.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.domain.exception.PaymentGatewayException;
import com.company.bsmsvc.domain.model.payment.SubscriptionResult;
import com.company.bsmsvc.domain.model.payment.UpdateSubscriptionCommand;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.SubscriptionItemCollection;
import com.stripe.model.Price;
import com.stripe.param.SubscriptionUpdateParams;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StripeUpdateHardeningTest {

    @Mock private StripeClientWrapper stripeClient;

    private StripePaymentAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new StripePaymentAdapter(stripeClient);
    }

    @Test
    void updateSubscription_updatesExistingItem_notAddsNew() throws Exception {
        String subId = "sub_test";
        String oldPriceId = "price_old";
        String newPriceId = "price_new";
        String itemId = "si_existing";

        Subscription current = mockSubscriptionWithItem(subId, itemId, oldPriceId);
        Subscription updated = new Subscription();
        updated.setId(subId);
        updated.setStatus("active");

        when(stripeClient.retrieveSubscription(subId)).thenReturn(current);
        when(stripeClient.updateSubscription(eq(subId), any())).thenReturn(updated);

        SubscriptionResult result = adapter.updateSubscription(new UpdateSubscriptionCommand(subId, newPriceId));

        assertThat(result.externalSubscriptionId()).isEqualTo(subId);

        ArgumentCaptor<SubscriptionUpdateParams> captor = ArgumentCaptor.forClass(SubscriptionUpdateParams.class);
        verify(stripeClient).updateSubscription(eq(subId), captor.capture());
        // Verify the item being updated has the existing itemId (not a new item added)
        List<?> items = captor.getValue().getItems();
        assertThat(items).hasSize(1);
    }

    @Test
    void updateSubscription_isIdempotent_whenPriceAlreadySet() throws Exception {
        String subId = "sub_test";
        String priceId = "price_same";
        String itemId = "si_existing";

        Subscription current = mockSubscriptionWithItem(subId, itemId, priceId);
        current.setStatus("active");
        when(stripeClient.retrieveSubscription(subId)).thenReturn(current);

        SubscriptionResult result = adapter.updateSubscription(new UpdateSubscriptionCommand(subId, priceId));

        assertThat(result.externalSubscriptionId()).isEqualTo(subId);
        // Should NOT call updateSubscription since price is already correct
        verify(stripeClient, never()).updateSubscription(any(), any());
    }

    @Test
    void updateSubscription_throwsWhenNoItemsFound() throws Exception {
        String subId = "sub_empty";
        Subscription current = new Subscription();
        current.setId(subId);
        SubscriptionItemCollection emptyItems = new SubscriptionItemCollection();
        emptyItems.setData(List.of());
        current.setItems(emptyItems);

        when(stripeClient.retrieveSubscription(subId)).thenReturn(current);

        assertThatThrownBy(() -> adapter.updateSubscription(new UpdateSubscriptionCommand(subId, "price_new")))
            .isInstanceOf(PaymentGatewayException.class)
            .hasMessageContaining("no items");
    }

    private Subscription mockSubscriptionWithItem(String subId, String itemId, String priceId) {
        Subscription sub = new Subscription();
        sub.setId(subId);
        sub.setStatus("active");

        SubscriptionItem item = new SubscriptionItem();
        item.setId(itemId);
        Price price = new Price();
        price.setId(priceId);
        item.setPrice(price);

        SubscriptionItemCollection itemCollection = new SubscriptionItemCollection();
        itemCollection.setData(List.of(item));
        sub.setItems(itemCollection);
        return sub;
    }
}
