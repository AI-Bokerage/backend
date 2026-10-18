package com.company.bsmsvc.infrastructure.gateway;

import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.PaymentMethodCollection;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentMethodAttachParams;
import com.stripe.param.PaymentMethodListParams;
import com.stripe.param.SubscriptionCancelParams;
import com.stripe.param.SubscriptionCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.CustomerCreateParams;

public interface StripeClientWrapper {
    Customer createCustomer(CustomerCreateParams params) throws StripeException;
    PaymentMethod retrievePaymentMethod(String paymentMethodId) throws StripeException;
    PaymentMethod attachPaymentMethod(String paymentMethodId, PaymentMethodAttachParams params) throws StripeException;
    PaymentMethod detachPaymentMethod(String paymentMethodId) throws StripeException;
    PaymentMethodCollection listPaymentMethods(PaymentMethodListParams params) throws StripeException;
    Subscription createSubscription(SubscriptionCreateParams params) throws StripeException;
    Subscription retrieveSubscription(String subscriptionId) throws StripeException;
    Subscription updateSubscription(String subscriptionId, SubscriptionUpdateParams params) throws StripeException;
    Subscription cancelSubscription(String subscriptionId, SubscriptionCancelParams params) throws StripeException;
    Session createCheckoutSession(SessionCreateParams params) throws StripeException;
    Session retrieveCheckoutSession(String sessionId) throws StripeException;
    PaymentIntent createPaymentIntent(PaymentIntentCreateParams params) throws StripeException;
    /** Creates a PaymentIntent with a Stripe idempotency key — safe for dunning retries. */
    PaymentIntent createPaymentIntentWithIdempotency(PaymentIntentCreateParams params, String idempotencyKey) throws StripeException;
    PaymentIntent retrievePaymentIntent(String paymentIntentId) throws StripeException;
}
