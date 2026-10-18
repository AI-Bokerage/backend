package com.company.bsmsvc.infrastructure.gateway;

import com.company.bsmsvc.config.StripeProperties;
import com.stripe.Stripe;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StripeClientWrapperImpl implements StripeClientWrapper {

    public StripeClientWrapperImpl(StripeProperties properties) {
        String key = properties == null ? null : properties.secretKey();
        if (key == null || key.isBlank()) {
            log.error("Stripe secret key is not configured (payment.stripe.secret-key)");
            throw new IllegalStateException("Stripe secret key is not configured (payment.stripe.secret-key)");
        }
        if (!key.startsWith("sk_")) {
            log.warn("Configured Stripe secret key does not look like a valid secret (will still attempt to use it). Masked: {}", mask(key));
        } else {
            log.info("Configured Stripe secret key present. Masked: {}", mask(key));
        }
        Stripe.apiKey = key;
    }

    private static String mask(String key) {
        if (key == null) return "<null>";
        int len = key.length();
        if (len <= 8) return "****";
        return "****" + key.substring(len - 8);
    }

    @Override
    public Customer createCustomer(CustomerCreateParams params) throws StripeException {
        return Customer.create(params);
    }

    @Override
    public PaymentMethod retrievePaymentMethod(String paymentMethodId) throws StripeException {
        return PaymentMethod.retrieve(paymentMethodId);
    }

    @Override
    public PaymentMethod attachPaymentMethod(String paymentMethodId, PaymentMethodAttachParams params) throws StripeException {
        PaymentMethod pm = PaymentMethod.retrieve(paymentMethodId);
        return pm.attach(params);
    }

    @Override
    public PaymentMethod detachPaymentMethod(String paymentMethodId) throws StripeException {
        PaymentMethod pm = PaymentMethod.retrieve(paymentMethodId);
        return pm.detach();
    }

    @Override
    public PaymentMethodCollection listPaymentMethods(PaymentMethodListParams params) throws StripeException {
        return PaymentMethod.list(params);
    }

    @Override
    public Subscription createSubscription(SubscriptionCreateParams params) throws StripeException {
        return Subscription.create(params);
    }

    @Override
    public Subscription retrieveSubscription(String subscriptionId) throws StripeException {
        return Subscription.retrieve(subscriptionId);
    }

    @Override
    public Subscription updateSubscription(String subscriptionId, SubscriptionUpdateParams params) throws StripeException {
        return Subscription.retrieve(subscriptionId).update(params);
    }

    @Override
    public Subscription cancelSubscription(String subscriptionId, SubscriptionCancelParams params) throws StripeException {
        return Subscription.retrieve(subscriptionId).cancel(params);
    }

    @Override
    public Session createCheckoutSession(SessionCreateParams params) throws StripeException {
        return Session.create(params);
    }

    @Override
    public Session retrieveCheckoutSession(String sessionId) throws StripeException {
        return Session.retrieve(sessionId);
    }

    @Override
    public PaymentIntent createPaymentIntent(PaymentIntentCreateParams params) throws StripeException {
        return PaymentIntent.create(params);
    }

    @Override
    public PaymentIntent createPaymentIntentWithIdempotency(PaymentIntentCreateParams params,
                                                            String idempotencyKey) throws StripeException {
        com.stripe.net.RequestOptions options = com.stripe.net.RequestOptions.builder()
            .setIdempotencyKey(idempotencyKey)
            .build();
        return PaymentIntent.create(params, options);
    }

    @Override
    public PaymentIntent retrievePaymentIntent(String paymentIntentId) throws StripeException {
        return PaymentIntent.retrieve(paymentIntentId);
    }
}
