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
import com.company.bsmsvc.domain.model.payment.PaymentMethodDetails;
import com.company.bsmsvc.domain.model.payment.RefundCommand;
import com.company.bsmsvc.domain.model.payment.RefundResult;
import com.company.bsmsvc.domain.model.payment.SubscriptionResult;
import com.company.bsmsvc.domain.model.payment.UpdateSubscriptionCommand;
import com.company.bsmsvc.domain.port.PaymentGatewayPort;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentMethod;
import com.stripe.model.PaymentMethodCollection;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentMethodAttachParams;
import com.stripe.param.PaymentMethodListParams;
import com.company.bsmsvc.domain.model.payment.RetryPaymentCommand;
import io.github.resilience4j.retry.annotation.Retry;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.SubscriptionCancelParams;
import com.stripe.param.SubscriptionCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import java.util.List;
import java.util.Map;
import com.company.bsmsvc.domain.model.payment.PaymentStatusResult;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Slf4j
@Component
public class StripePaymentAdapter implements PaymentGatewayPort {

    private final StripeClientWrapper stripeClient;

    public StripePaymentAdapter(StripeClientWrapper stripeClient) {
        this.stripeClient = stripeClient;
    }

    @Override
    public CustomerResult createCustomer(CreateCustomerCommand command) {
        Assert.notNull(command, "command must not be null");
        Assert.notNull(command.tenantId(), "tenantId must not be null");
        log.info("Stripe.createCustomer tenantId={}", command.tenantId());
        try {
            CustomerCreateParams params = CustomerCreateParams.builder()
                .setName(command.name())
                .setEmail(command.email())
                .putMetadata("tenantId", command.tenantId().toString())
                .build();
            com.stripe.model.Customer customer = stripeClient.createCustomer(params);
            log.info("Stripe.createCustomer success customerId={} tenantId={}", customer.getId(), command.tenantId());
            return new CustomerResult(customer.getId());
        } catch (StripeException e) {
            log.error("Stripe.createCustomer failed tenantId={}", command.tenantId(), e);
            throw new PaymentGatewayException("Stripe customer creation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void attachPaymentMethod(AttachPaymentMethodCommand command) {
        Assert.notNull(command, "command must not be null");
        Assert.hasText(command.externalCustomerId(), "externalCustomerId must not be blank");
        Assert.hasText(command.paymentMethodToken(), "paymentMethodToken must not be blank");
        log.info("Stripe.attachPaymentMethod customerId={} pmToken={}", command.externalCustomerId(), command.paymentMethodToken());
        try {
            PaymentMethodAttachParams params = PaymentMethodAttachParams.builder()
                .setCustomer(command.externalCustomerId())
                .build();
            stripeClient.attachPaymentMethod(command.paymentMethodToken(), params);
            log.info("Stripe.attachPaymentMethod success pmToken={}", command.paymentMethodToken());
        } catch (StripeException e) {
            log.error("Stripe.attachPaymentMethod failed pmToken={}", command.paymentMethodToken(), e);
            throw new PaymentGatewayException("Stripe payment method attach failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void detachPaymentMethod(DetachPaymentMethodCommand command) {
        Assert.notNull(command, "command must not be null");
        Assert.hasText(command.paymentMethodId(), "paymentMethodId must not be blank");
        log.info("Stripe.detachPaymentMethod pmId={}", command.paymentMethodId());
        try {
            stripeClient.detachPaymentMethod(command.paymentMethodId());
            log.info("Stripe.detachPaymentMethod success pmId={}", command.paymentMethodId());
        } catch (StripeException e) {
            log.error("Stripe.detachPaymentMethod failed pmId={}", command.paymentMethodId(), e);
            throw new PaymentGatewayException("Stripe payment method detach failed: " + e.getMessage(), e);
        }
    }

    @Override
    public ListPaymentMethodsResult listPaymentMethods(ListPaymentMethodsCommand command) {
        Assert.notNull(command, "command must not be null");
        Assert.hasText(command.externalCustomerId(), "externalCustomerId must not be blank");
        log.info("Stripe.listPaymentMethods customerId={}", command.externalCustomerId());
        try {
            PaymentMethodListParams params = PaymentMethodListParams.builder()
                .setCustomer(command.externalCustomerId())
                .setType(PaymentMethodListParams.Type.CARD)
                .build();
            PaymentMethodCollection collection = stripeClient.listPaymentMethods(params);
            List<PaymentMethodDetails> details = collection.getData().stream()
                .map(pm -> toDetails(pm))
                .toList();
            return new ListPaymentMethodsResult(details);
        } catch (StripeException e) {
            log.error("Stripe.listPaymentMethods failed customerId={}", command.externalCustomerId(), e);
            throw new PaymentGatewayException("Stripe list payment methods failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Retry(name = "stripeGateway")
    public SubscriptionResult createSubscription(CreateSubscriptionCommand command) {
        Assert.notNull(command, "command must not be null");
        Assert.hasText(command.externalCustomerId(), "externalCustomerId must not be blank");
        Assert.hasText(command.externalPriceId(), "externalPriceId must not be blank");
        log.info("Stripe.createSubscription customerId={} priceId={}", command.externalCustomerId(), command.externalPriceId());
        try {
            SubscriptionCreateParams.Builder builder = SubscriptionCreateParams.builder()
                .setCustomer(command.externalCustomerId())
                .addItem(SubscriptionCreateParams.Item.builder()
                    .setPrice(command.externalPriceId())
                    .build())
                // Off-session so Stripe knows this is a server-initiated subscription
                .setPaymentBehavior(SubscriptionCreateParams.PaymentBehavior.DEFAULT_INCOMPLETE);
            if (command.defaultPaymentMethodId() != null) {
                builder.setDefaultPaymentMethod(command.defaultPaymentMethodId());
            }
            // Defer Stripe billing until BSM trial ends — prevents charging the customer
            // while BSM shows TRIALING.  Without this Stripe bills immediately on Day 0
            // and BSM bills again when the trial expires → double charge.
            if (command.trialEnd() != null) {
                builder.setTrialEnd(command.trialEnd().getEpochSecond());
            }
            if (command.metadata() != null) builder.putAllMetadata(command.metadata());
            com.stripe.model.Subscription sub = stripeClient.createSubscription(builder.build());
            log.info("Stripe.createSubscription success subscriptionId={}", sub.getId());
            return new SubscriptionResult(sub.getId(), sub.getStatus());
        } catch (StripeException e) {
            log.error("Stripe.createSubscription failed customerId={}", command.externalCustomerId(), e);
            throw new PaymentGatewayException("Stripe subscription creation failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Retry(name = "stripeGateway")
    public SubscriptionResult updateSubscription(UpdateSubscriptionCommand command) {
        Assert.notNull(command, "command must not be null");
        Assert.hasText(command.externalSubscriptionId(), "externalSubscriptionId must not be blank");
        Assert.hasText(command.externalPriceId(), "externalPriceId must not be blank");
        log.info("Stripe.updateSubscription subscriptionId={} newPriceId={}", command.externalSubscriptionId(), command.externalPriceId());
        try {
            // Retrieve current subscription to get existing item ID (prevents duplicate items)
            com.stripe.model.Subscription current = stripeClient.retrieveSubscription(command.externalSubscriptionId());
            var items = current.getItems().getData();
            if (items.isEmpty()) {
                throw new PaymentGatewayException("Stripe subscription has no items: " + command.externalSubscriptionId());
            }
            String existingItemId = items.get(0).getId();
            String currentPriceId = items.get(0).getPrice() != null ? items.get(0).getPrice().getId() : null;

            // Skip update if price is already correct (idempotent)
            if (command.externalPriceId().equals(currentPriceId)) {
                log.info("Stripe.updateSubscription skipped — price already set subscriptionId={}", command.externalSubscriptionId());
                return new SubscriptionResult(current.getId(), current.getStatus());
            }

            // Update the existing item (not add a new one)
            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                .addItem(SubscriptionUpdateParams.Item.builder()
                    .setId(existingItemId)
                    .setPrice(command.externalPriceId())
                    .build())
                .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.ALWAYS_INVOICE)
                .build();
            com.stripe.model.Subscription sub = stripeClient.updateSubscription(command.externalSubscriptionId(), params);
            log.info("Stripe.updateSubscription success subscriptionId={} newPriceId={}", sub.getId(), command.externalPriceId());
            return new SubscriptionResult(sub.getId(), sub.getStatus());
        } catch (PaymentGatewayException e) {
            throw e;
        } catch (StripeException e) {
            log.error("Stripe.updateSubscription failed subscriptionId={}", command.externalSubscriptionId(), e);
            throw new PaymentGatewayException("Stripe subscription update failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Retry(name = "stripeGateway")
    public void cancelSubscription(CancelSubscriptionCommand command) {
        Assert.notNull(command, "command must not be null");
        Assert.hasText(command.externalSubscriptionId(), "externalSubscriptionId must not be blank");
        log.info("Stripe.cancelSubscription subscriptionId={} immediately={}", command.externalSubscriptionId(), command.immediately());
        try {
            if (command.immediately()) {
                stripeClient.cancelSubscription(command.externalSubscriptionId(), SubscriptionCancelParams.builder().build());
            } else {
                SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .setCancelAtPeriodEnd(true)
                    .build();
                stripeClient.updateSubscription(command.externalSubscriptionId(), params);
            }
            log.info("Stripe.cancelSubscription success subscriptionId={}", command.externalSubscriptionId());
        } catch (StripeException e) {
            log.error("Stripe.cancelSubscription failed subscriptionId={}", command.externalSubscriptionId(), e);
            throw new PaymentGatewayException("Stripe subscription cancellation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public CheckoutSessionResult createCheckoutSession(CreateCheckoutSessionCommand command) {
        Assert.notNull(command, "command must not be null");
        Assert.hasText(command.externalCustomerId(), "externalCustomerId must not be blank");
        Assert.isTrue(command.amountMinor() > 0, "amountMinor must be > 0");
        Assert.hasText(command.currency(), "currency must not be blank");
        log.info("Stripe.createCheckoutSession customerId={} amount={} currency={}",
            command.externalCustomerId(), command.amountMinor(), command.currency());
        try {
            SessionCreateParams.LineItem lineItem = SessionCreateParams.LineItem.builder()
                .setQuantity(1L)
                .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                    .setCurrency(command.currency().toLowerCase())
                    .setUnitAmount(command.amountMinor())
                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                        .setName(command.description() != null ? command.description() : "Invoice Payment")
                        .build())
                    .build())
                .build();
            SessionCreateParams.Builder builder = SessionCreateParams.builder()
                .setCustomer(command.externalCustomerId())
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(command.successUrl())
                .setCancelUrl(command.cancelUrl())
                .addLineItem(lineItem);
            if (command.metadata() != null) builder.putAllMetadata(command.metadata());
            com.stripe.model.checkout.Session session = stripeClient.createCheckoutSession(builder.build());
            log.info("Stripe.createCheckoutSession success sessionId={}", session.getId());
            return new CheckoutSessionResult(session.getId(), session.getUrl());
        } catch (StripeException e) {
            log.error("Stripe.createCheckoutSession failed customerId={}", command.externalCustomerId(), e);
            throw new PaymentGatewayException("Stripe checkout session creation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentStatusResult retrievePaymentStatus(String externalId) {
        Assert.hasText(externalId, "externalId must not be blank");
        log.info("Stripe.retrievePaymentStatus externalId={}", externalId);
        try {
            if (externalId.startsWith("cs_")) {
                com.stripe.model.checkout.Session session = stripeClient.retrieveCheckoutSession(externalId);
                boolean succeeded = "complete".equals(session.getStatus());
                return new PaymentStatusResult(externalId, session.getStatus(), session.getPaymentIntent(), succeeded, false);
            } else if (externalId.startsWith("pi_")) {
                com.stripe.model.PaymentIntent intent = stripeClient.retrievePaymentIntent(externalId);
                boolean succeeded = "succeeded".equals(intent.getStatus());
                boolean failed = "canceled".equals(intent.getStatus()) || "requires_payment_method".equals(intent.getStatus());
                return new PaymentStatusResult(externalId, intent.getStatus(), intent.getLatestCharge(), succeeded, failed);
            }
            throw new PaymentGatewayException("Unknown Stripe payment ID format: " + externalId);
        } catch (PaymentGatewayException e) {
            throw e;
        } catch (StripeException e) {
            log.error("Stripe.retrievePaymentStatus failed externalId={}", externalId, e);
            throw new PaymentGatewayException("Stripe payment status retrieval failed: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentIntentResult createPaymentIntent(CreatePaymentIntentCommand command) {
        Assert.notNull(command, "command must not be null");
        Assert.hasText(command.externalCustomerId(), "externalCustomerId must not be blank");
        log.info("Stripe.createPaymentIntent customerId={} amount={} currency={}", command.externalCustomerId(), command.amountMinor(), command.currency());
        try {
            PaymentIntentCreateParams.Builder builder = PaymentIntentCreateParams.builder()
                .setAmount(command.amountMinor())
                .setCurrency(command.currency())
                .setCustomer(command.externalCustomerId())
                .setConfirmationMethod(PaymentIntentCreateParams.ConfirmationMethod.AUTOMATIC);
            if (command.metadata() != null) builder.putAllMetadata(command.metadata());
            com.stripe.model.PaymentIntent intent = stripeClient.createPaymentIntent(builder.build());
            log.info("Stripe.createPaymentIntent success intentId={}", intent.getId());
            return new PaymentIntentResult(intent.getId(), intent.getClientSecret(), intent.getStatus());
        } catch (StripeException e) {
            log.error("Stripe.createPaymentIntent failed", e);
            throw new PaymentGatewayException("Stripe payment intent creation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentIntentResult retryPayment(RetryPaymentCommand command) {
        Assert.notNull(command, "command must not be null");
        Assert.hasText(command.externalCustomerId(), "externalCustomerId must not be blank");
        Assert.hasText(command.paymentMethodId(), "paymentMethodId must not be blank");
        log.info("Stripe.retryPayment customerId={} amount={} pmId={}", command.externalCustomerId(), command.amountMinor(), command.paymentMethodId());
        try {
            PaymentIntentCreateParams.Builder builder = PaymentIntentCreateParams.builder()
                .setAmount(command.amountMinor())
                .setCurrency(command.currency())
                .setCustomer(command.externalCustomerId())
                .setPaymentMethod(command.paymentMethodId())
                .setConfirm(true)
                .setOffSession(true);
            if (command.metadata() != null) builder.putAllMetadata(command.metadata());
            com.stripe.model.PaymentIntent intent;
            if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
                // Idempotency key derived from dunning attempt ID ensures that retrying the
                // same attempt after an OLE rollback returns the same PaymentIntent rather
                // than creating a new charge — preventing double billing.
                intent = stripeClient.createPaymentIntentWithIdempotency(builder.build(), command.idempotencyKey());
            } else {
                intent = stripeClient.createPaymentIntent(builder.build());
            }
            log.info("Stripe.retryPayment success intentId={} status={}", intent.getId(), intent.getStatus());
            return new PaymentIntentResult(intent.getId(), intent.getClientSecret(), intent.getStatus());
        } catch (StripeException e) {
            log.error("Stripe.retryPayment failed customerId={} code={}", command.externalCustomerId(), e.getCode(), e);
            throw new PaymentGatewayException("Stripe dunning retry failed: " + e.getMessage(), e);
        }
    }

    @Override
    public RefundResult refund(RefundCommand command) {
        Assert.notNull(command, "command must not be null");
        Assert.hasText(command.externalChargeId(), "externalChargeId must not be blank");
        log.info("Stripe.refund ref={} amount={}", command.externalChargeId(), command.amountMinor());
        try {
            RefundCreateParams.Builder builder = RefundCreateParams.builder()
                .setAmount(command.amountMinor())
                .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER);
            // Checkout-session payments store the PaymentIntent ID (pi_xxx) as the charge reference.
            // Direct PaymentIntent flows store the Charge ID (ch_xxx). Handle both.
            if (command.externalChargeId().startsWith("pi_")) {
                builder.setPaymentIntent(command.externalChargeId());
            } else {
                builder.setCharge(command.externalChargeId());
            }
            com.stripe.model.Refund refund = com.stripe.model.Refund.create(builder.build());
            log.info("Stripe.refund success refundId={} status={}", refund.getId(), refund.getStatus());
            return new RefundResult(refund.getId(), refund.getStatus());
        } catch (StripeException e) {
            log.error("Stripe.refund failed ref={}", command.externalChargeId(), e);
            throw new PaymentGatewayException("Stripe refund failed: " + e.getMessage(), e);
        }
    }

    private PaymentMethodDetails toDetails(PaymentMethod pm) {
        PaymentMethod.Card card = pm.getCard();
        return new PaymentMethodDetails(
            pm.getId(),
            pm.getType(),
            card != null ? card.getBrand() : null,
            card != null ? card.getLast4() : null,
            card != null ? card.getExpMonth() != null ? card.getExpMonth().intValue() : null : null,
            card != null ? card.getExpYear() != null ? card.getExpYear().intValue() : null : null
        );
    }
}
