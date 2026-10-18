package com.example.bsmdemo.adapter;

import com.company.bsmsvc.domain.model.payment.AttachPaymentMethodCommand;
import com.company.bsmsvc.domain.model.payment.CancelSubscriptionCommand;
import com.company.bsmsvc.domain.model.payment.CheckoutSessionResult;
import com.company.bsmsvc.domain.model.payment.CreateCheckoutSessionCommand;
import com.company.bsmsvc.domain.model.payment.CreateCustomerCommand;
import com.company.bsmsvc.domain.model.payment.CreatePaymentIntentCommand;
import com.company.bsmsvc.domain.model.payment.CreateSubscriptionCommand;
import com.company.bsmsvc.domain.model.payment.CustomerResult;
import com.company.bsmsvc.domain.model.payment.DetachPaymentMethodCommand;
import com.company.bsmsvc.domain.model.payment.ListPaymentMethodsCommand;
import com.company.bsmsvc.domain.model.payment.ListPaymentMethodsResult;
import com.company.bsmsvc.domain.model.payment.PaymentIntentResult;
import com.company.bsmsvc.domain.model.payment.PaymentStatusResult;
import com.company.bsmsvc.domain.model.payment.RefundCommand;
import com.company.bsmsvc.domain.model.payment.RefundResult;
import com.company.bsmsvc.domain.model.payment.RetryPaymentCommand;
import com.company.bsmsvc.domain.model.payment.SubscriptionResult;
import com.company.bsmsvc.domain.model.payment.UpdateSubscriptionCommand;
import com.company.bsmsvc.domain.port.PaymentGatewayPort;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Deterministic always-succeeds stand-in for a real gateway (Stripe/Razorpay).
 * No HTTP calls — every operation simulates immediate success.
 */
@Component
public class DemoPaymentGatewayAdapter implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(DemoPaymentGatewayAdapter.class);

    @Override
    public CustomerResult createCustomer(CreateCustomerCommand command) {
        String externalCustomerId = "demo_cus_" + UUID.randomUUID();
        log.info("Demo gateway: created customer {} for tenant {}", externalCustomerId, command.tenantId());
        return new CustomerResult(externalCustomerId);
    }

    @Override
    public void attachPaymentMethod(AttachPaymentMethodCommand command) {
        log.info("Demo gateway: attached payment method token {} to customer {}",
            command.paymentMethodToken(), command.externalCustomerId());
    }

    @Override
    public void detachPaymentMethod(DetachPaymentMethodCommand command) {
        log.info("Demo gateway: detached payment method {} from customer {}",
            command.paymentMethodId(), command.externalCustomerId());
    }

    @Override
    public SubscriptionResult createSubscription(CreateSubscriptionCommand command) {
        String externalSubscriptionId = "demo_sub_" + UUID.randomUUID();
        log.info("Demo gateway: created subscription {} for customer {}", externalSubscriptionId, command.externalCustomerId());
        return new SubscriptionResult(externalSubscriptionId, "active");
    }

    @Override
    public SubscriptionResult updateSubscription(UpdateSubscriptionCommand command) {
        log.info("Demo gateway: updated subscription {} to price {}", command.externalSubscriptionId(), command.externalPriceId());
        return new SubscriptionResult(command.externalSubscriptionId(), "active");
    }

    @Override
    public void cancelSubscription(CancelSubscriptionCommand command) {
        log.info("Demo gateway: cancelled subscription {} (immediately={})", command.externalSubscriptionId(), command.immediately());
    }

    @Override
    public CheckoutSessionResult createCheckoutSession(CreateCheckoutSessionCommand command) {
        String sessionId = "demo_cs_" + UUID.randomUUID();
        String checkoutUrl = "https://demo.bsm.local/checkout/" + sessionId;
        log.info("Demo gateway: created checkout session {} for {} {}", sessionId, command.amountMinor(), command.currency());
        return new CheckoutSessionResult(sessionId, checkoutUrl);
    }

    @Override
    public ListPaymentMethodsResult listPaymentMethods(ListPaymentMethodsCommand command) {
        log.info("Demo gateway: listed payment methods for customer {}", command.externalCustomerId());
        return new ListPaymentMethodsResult(List.of());
    }

    @Override
    public PaymentIntentResult createPaymentIntent(CreatePaymentIntentCommand command) {
        String paymentIntentId = "demo_pi_" + UUID.randomUUID();
        log.info("Demo gateway: created payment intent {} for {} {}", paymentIntentId, command.amountMinor(), command.currency());
        return new PaymentIntentResult(paymentIntentId, "demo_secret_" + paymentIntentId, "SUCCEEDED");
    }

    @Override
    public PaymentIntentResult retryPayment(RetryPaymentCommand command) {
        String paymentIntentId = "demo_pi_retry_" + UUID.randomUUID();
        log.info("Demo gateway: retried payment {} for {} {}", paymentIntentId, command.amountMinor(), command.currency());
        return new PaymentIntentResult(paymentIntentId, "demo_secret_" + paymentIntentId, "SUCCEEDED");
    }

    @Override
    public PaymentStatusResult retrievePaymentStatus(String externalId) {
        log.info("Demo gateway: retrieved payment status for {}", externalId);
        return new PaymentStatusResult(externalId, "SUCCEEDED", "demo_ch_" + externalId, true, false);
    }

    @Override
    public RefundResult refund(RefundCommand command) {
        String refundId = "demo_re_" + UUID.randomUUID();
        log.info("Demo gateway: refunded {} minor units for charge {}", command.amountMinor(), command.externalChargeId());
        return new RefundResult(refundId, "SUCCEEDED");
    }
}
