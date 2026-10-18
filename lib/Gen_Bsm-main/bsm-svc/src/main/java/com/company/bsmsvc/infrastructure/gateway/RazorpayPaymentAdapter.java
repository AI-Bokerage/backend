package com.company.bsmsvc.infrastructure.gateway;

import com.company.bsmsvc.domain.exception.PaymentGatewayException;
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
import com.company.bsmsvc.domain.model.payment.RetryPaymentCommand;
import com.company.bsmsvc.domain.model.payment.RefundCommand;
import com.company.bsmsvc.domain.model.payment.RefundResult;
import com.company.bsmsvc.domain.model.payment.SubscriptionResult;
import com.company.bsmsvc.domain.model.payment.UpdateSubscriptionCommand;
import com.company.bsmsvc.domain.port.PaymentGatewayPort;
import com.razorpay.RazorpayException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Slf4j
@Component
public class RazorpayPaymentAdapter implements PaymentGatewayPort {

    private final RazorpayClientWrapper razorpayClient;

    public RazorpayPaymentAdapter(RazorpayClientWrapper razorpayClient) {
        this.razorpayClient = razorpayClient;
    }

    @Override
    public CustomerResult createCustomer(CreateCustomerCommand command) {
        Assert.notNull(command, "command must not be null");
        Assert.notNull(command.tenantId(), "tenantId must not be null");
        log.info("Razorpay.createCustomer tenantId={}", command.tenantId());
        try {
            JSONObject data = new JSONObject();
            if (command.name() != null) data.put("name", command.name());
            if (command.email() != null) data.put("email", command.email());
            JSONObject result = razorpayClient.createCustomer(data);
            String customerId = result.getString("id");
            log.info("Razorpay.createCustomer success customerId={} tenantId={}", customerId, command.tenantId());
            return new CustomerResult(customerId);
        } catch (RazorpayException e) {
            log.error("Razorpay.createCustomer failed tenantId={}", command.tenantId(), e);
            throw new PaymentGatewayException("Razorpay customer creation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void attachPaymentMethod(AttachPaymentMethodCommand command) {
        Assert.notNull(command, "command must not be null");
        Assert.hasText(command.externalCustomerId(), "externalCustomerId must not be blank");
        log.info("Razorpay.attachPaymentMethod customerId={}", command.externalCustomerId());
        throw new UnsupportedOperationException("Razorpay payment method attachment is handled via the Checkout flow");
    }

    @Override
    public void detachPaymentMethod(DetachPaymentMethodCommand command) {
        Assert.notNull(command, "command must not be null");
        Assert.hasText(command.paymentMethodId(), "paymentMethodId must not be blank");
        log.info("Razorpay.detachPaymentMethod pmId={}", command.paymentMethodId());
        throw new UnsupportedOperationException("Razorpay detachPaymentMethod integration pending");
    }

    @Override
    public ListPaymentMethodsResult listPaymentMethods(ListPaymentMethodsCommand command) {
        Assert.notNull(command, "command must not be null");
        Assert.hasText(command.externalCustomerId(), "externalCustomerId must not be blank");
        log.info("Razorpay.listPaymentMethods customerId={}", command.externalCustomerId());
        return new ListPaymentMethodsResult(List.of());
    }

    @Override
    public SubscriptionResult createSubscription(CreateSubscriptionCommand command) {
        Assert.notNull(command, "command must not be null");
        Assert.hasText(command.externalCustomerId(), "externalCustomerId must not be blank");
        Assert.hasText(command.externalPriceId(), "externalPriceId must not be blank");
        log.info("Razorpay.createSubscription customerId={} planId={}", command.externalCustomerId(), command.externalPriceId());
        try {
            JSONObject data = new JSONObject();
            data.put("plan_id", command.externalPriceId());
            data.put("customer_notify", 0);
            data.put("quantity", 1);
            data.put("total_count", 120);
            Map<String, String> meta = command.metadata();
            if (meta != null) {
                JSONObject notes = new JSONObject();
                meta.forEach(notes::put);
                data.put("notes", notes);
            }
            com.razorpay.Subscription sub = razorpayClient.createSubscription(data);
            String subId = sub.get("id").toString();
            String status = sub.get("status").toString();
            log.info("Razorpay.createSubscription success subscriptionId={}", subId);
            return new SubscriptionResult(subId, status);
        } catch (RazorpayException e) {
            log.error("Razorpay.createSubscription failed", e);
            throw new PaymentGatewayException("Razorpay subscription creation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public SubscriptionResult updateSubscription(UpdateSubscriptionCommand command) {
        Assert.notNull(command, "command must not be null");
        Assert.hasText(command.externalSubscriptionId(), "externalSubscriptionId must not be blank");
        log.info("Razorpay.updateSubscription subscriptionId={}", command.externalSubscriptionId());
        try {
            JSONObject data = new JSONObject();
            if (command.externalPriceId() != null) data.put("plan_id", command.externalPriceId());
            com.razorpay.Subscription sub = razorpayClient.updateSubscription(command.externalSubscriptionId(), data);
            String status = sub.get("status").toString();
            log.info("Razorpay.updateSubscription success subscriptionId={}", command.externalSubscriptionId());
            return new SubscriptionResult(command.externalSubscriptionId(), status);
        } catch (RazorpayException e) {
            log.error("Razorpay.updateSubscription failed subscriptionId={}", command.externalSubscriptionId(), e);
            throw new PaymentGatewayException("Razorpay subscription update failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void cancelSubscription(CancelSubscriptionCommand command) {
        Assert.notNull(command, "command must not be null");
        Assert.hasText(command.externalSubscriptionId(), "externalSubscriptionId must not be blank");
        log.info("Razorpay.cancelSubscription subscriptionId={} immediately={}", command.externalSubscriptionId(), command.immediately());
        try {
            // cancelAtCycleEnd=true means cancel at end of billing cycle (not immediate); invert immediately flag
            razorpayClient.cancelSubscription(command.externalSubscriptionId(), !command.immediately());
            log.info("Razorpay.cancelSubscription success subscriptionId={}", command.externalSubscriptionId());
        } catch (RazorpayException e) {
            log.error("Razorpay.cancelSubscription failed subscriptionId={}", command.externalSubscriptionId(), e);
            throw new PaymentGatewayException("Razorpay subscription cancellation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public CheckoutSessionResult createCheckoutSession(CreateCheckoutSessionCommand command) {
        Assert.notNull(command, "command must not be null");
        Assert.hasText(command.externalCustomerId(), "externalCustomerId must not be blank");
        Assert.isTrue(command.amountMinor() > 0, "amountMinor must be > 0");
        log.info("Razorpay.createCheckoutSession customerId={} amount={} currency={}",
            command.externalCustomerId(), command.amountMinor(), command.currency());
        try {
            JSONObject data = new JSONObject();
            data.put("amount", command.amountMinor());
            data.put("currency", command.currency().toUpperCase());
            data.put("customer_id", command.externalCustomerId());
            if (command.metadata() != null) {
                JSONObject notes = new JSONObject();
                command.metadata().forEach(notes::put);
                data.put("notes", notes);
            }
            JSONObject order = razorpayClient.createOrder(data);
            String orderId = order.getString("id");
            log.info("Razorpay.createCheckoutSession success orderId={}", orderId);
            return new CheckoutSessionResult(orderId, command.successUrl());
        } catch (RazorpayException e) {
            log.error("Razorpay.createCheckoutSession failed", e);
            throw new PaymentGatewayException("Razorpay checkout order creation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentIntentResult createPaymentIntent(CreatePaymentIntentCommand command) {
        Assert.notNull(command, "command must not be null");
        Assert.hasText(command.externalCustomerId(), "externalCustomerId must not be blank");
        log.info("Razorpay.createPaymentIntent customerId={} amount={} currency={}", command.externalCustomerId(), command.amountMinor(), command.currency());
        try {
            JSONObject data = new JSONObject();
            data.put("amount", command.amountMinor());
            data.put("currency", command.currency());
            data.put("customer_id", command.externalCustomerId());
            JSONObject order = razorpayClient.createOrder(data);
            String orderId = order.getString("id");
            log.info("Razorpay.createPaymentIntent success orderId={}", orderId);
            return new PaymentIntentResult(orderId, null, order.getString("status"));
        } catch (RazorpayException e) {
            log.error("Razorpay.createPaymentIntent failed", e);
            throw new PaymentGatewayException("Razorpay order creation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentIntentResult retryPayment(RetryPaymentCommand command) {
        Assert.notNull(command, "command must not be null");
        log.info("Razorpay.retryPayment customerId={} amount={}", command.externalCustomerId(), command.amountMinor());
        // Razorpay retry uses the same order creation flow as payment intent
        return createPaymentIntent(new CreatePaymentIntentCommand(
            null, command.externalCustomerId(), command.amountMinor(), command.currency(), command.metadata()
        ));
    }

    @Override
    public RefundResult refund(RefundCommand command) {
        Assert.notNull(command, "command must not be null");
        Assert.hasText(command.externalChargeId(), "externalChargeId must not be blank");
        log.info("Razorpay.refund paymentId={} amount={}", command.externalChargeId(), command.amountMinor());
        try {
            JSONObject data = new JSONObject();
            data.put("amount", command.amountMinor());
            data.put("notes", new JSONObject().put("reason", command.reason()));
            JSONObject refund = razorpayClient.createRefund(command.externalChargeId(), data);
            String refundId = refund.getString("id");
            log.info("Razorpay.refund success refundId={}", refundId);
            return new RefundResult(refundId, refund.getString("status"));
        } catch (RazorpayException e) {
            log.error("Razorpay.refund failed paymentId={}", command.externalChargeId(), e);
            throw new PaymentGatewayException("Razorpay refund failed: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentStatusResult retrievePaymentStatus(String externalId) {
        Assert.hasText(externalId, "externalId must not be blank");
        log.info("Razorpay.retrievePaymentStatus orderId={}", externalId);
        try {
            JSONObject order = razorpayClient.fetchOrder(externalId);
            String status = order.optString("status", "created");
            boolean succeeded = "paid".equals(status);
            boolean failed = "expired".equals(status);
            return new PaymentStatusResult(externalId, status, null, succeeded, failed);
        } catch (RazorpayException e) {
            log.error("Razorpay.retrievePaymentStatus failed orderId={}", externalId, e);
            throw new PaymentGatewayException("Razorpay order status retrieval failed: " + e.getMessage(), e);
        }
    }
}
