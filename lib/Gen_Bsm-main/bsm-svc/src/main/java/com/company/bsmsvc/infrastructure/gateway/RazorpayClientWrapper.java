package com.company.bsmsvc.infrastructure.gateway;

import com.razorpay.RazorpayException;
import com.razorpay.Subscription;
import org.json.JSONObject;

public interface RazorpayClientWrapper {
    JSONObject createCustomer(JSONObject data) throws RazorpayException;
    JSONObject fetchCustomer(String customerId) throws RazorpayException;
    JSONObject addToken(String customerId, JSONObject data) throws RazorpayException;
    JSONObject deleteToken(String customerId, String tokenId) throws RazorpayException;
    JSONObject fetchTokens(String customerId) throws RazorpayException;
    Subscription createSubscription(JSONObject data) throws RazorpayException;
    Subscription updateSubscription(String subscriptionId, JSONObject data) throws RazorpayException;
    void cancelSubscription(String subscriptionId, boolean cancelAtCycleEnd) throws RazorpayException;
    JSONObject createOrder(JSONObject data) throws RazorpayException;
    JSONObject fetchOrder(String orderId) throws RazorpayException;
    JSONObject createRefund(String paymentId, JSONObject data) throws RazorpayException;
}
