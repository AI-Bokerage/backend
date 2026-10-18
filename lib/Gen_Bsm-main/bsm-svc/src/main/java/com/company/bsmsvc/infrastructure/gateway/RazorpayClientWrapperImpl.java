package com.company.bsmsvc.infrastructure.gateway;

import com.company.bsmsvc.config.RazorpayProperties;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Subscription;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

@Component
public class RazorpayClientWrapperImpl implements RazorpayClientWrapper {

    private final RazorpayClient client;

    public RazorpayClientWrapperImpl(RazorpayProperties properties) throws RazorpayException {
        this.client = new RazorpayClient(properties.keyId(), properties.keySecret());
    }

    @Override
    public JSONObject createCustomer(JSONObject data) throws RazorpayException {
        com.razorpay.Customer customer = client.customers.create(data);
        return customer.toJson();
    }

    @Override
    public JSONObject fetchCustomer(String customerId) throws RazorpayException {
        com.razorpay.Customer customer = client.customers.fetch(customerId);
        return customer.toJson();
    }

    @Override
    public JSONObject addToken(String customerId, JSONObject data) throws RazorpayException {
        throw new UnsupportedOperationException("Razorpay token creation is handled via Checkout flow");
    }

    @Override
    public JSONObject deleteToken(String customerId, String tokenId) throws RazorpayException {
        throw new UnsupportedOperationException("Razorpay token deletion integration pending");
    }

    @Override
    public JSONObject fetchTokens(String customerId) throws RazorpayException {
        throw new UnsupportedOperationException("Razorpay token listing integration pending");
    }

    @Override
    public Subscription createSubscription(JSONObject data) throws RazorpayException {
        return client.subscriptions.create(data);
    }

    @Override
    public Subscription updateSubscription(String subscriptionId, JSONObject data) throws RazorpayException {
        return client.subscriptions.update(subscriptionId, data);
    }

    @Override
    public void cancelSubscription(String subscriptionId, boolean cancelAtCycleEnd) throws RazorpayException {
        JSONObject params = new JSONObject();
        params.put("cancel_at_cycle_end", cancelAtCycleEnd ? 1 : 0);
        client.subscriptions.cancel(subscriptionId, params);
    }

    @Override
    public JSONObject createOrder(JSONObject data) throws RazorpayException {
        com.razorpay.Order order = client.orders.create(data);
        return order.toJson();
    }

    @Override
    public JSONObject fetchOrder(String orderId) throws RazorpayException {
        com.razorpay.Order order = client.orders.fetch(orderId);
        return order.toJson();
    }

    @Override
    public JSONObject createRefund(String paymentId, JSONObject data) throws RazorpayException {
        com.razorpay.Refund refund = client.payments.refund(paymentId, data);
        return refund.toJson();
    }
}
