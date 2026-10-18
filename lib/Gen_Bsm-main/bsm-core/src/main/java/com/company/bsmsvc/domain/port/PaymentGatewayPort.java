package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.model.payment.AttachPaymentMethodCommand;
import com.company.bsmsvc.domain.model.payment.CancelSubscriptionCommand;
import com.company.bsmsvc.domain.model.payment.CheckoutSessionResult;
import com.company.bsmsvc.domain.model.payment.CreateCheckoutSessionCommand;
import com.company.bsmsvc.domain.model.payment.CreateCustomerCommand;
import com.company.bsmsvc.domain.model.payment.CreateSubscriptionCommand;
import com.company.bsmsvc.domain.model.payment.CustomerResult;
import com.company.bsmsvc.domain.model.payment.DetachPaymentMethodCommand;
import com.company.bsmsvc.domain.model.payment.CreatePaymentIntentCommand;
import com.company.bsmsvc.domain.model.payment.ListPaymentMethodsCommand;
import com.company.bsmsvc.domain.model.payment.ListPaymentMethodsResult;
import com.company.bsmsvc.domain.model.payment.PaymentIntentResult;
import com.company.bsmsvc.domain.model.payment.PaymentStatusResult;
import com.company.bsmsvc.domain.model.payment.RetryPaymentCommand;
import com.company.bsmsvc.domain.model.payment.RefundCommand;
import com.company.bsmsvc.domain.model.payment.RefundResult;
import com.company.bsmsvc.domain.model.payment.SubscriptionResult;
import com.company.bsmsvc.domain.model.payment.UpdateSubscriptionCommand;

/**
 * Abstraction over an external payment gateway (customer, payment method, provider-side
 * subscription mirroring, checkout session, payment intent, and refund operations). One
 * implementation per gateway (e.g. Stripe, Razorpay); resolved at runtime via
 * {@link com.company.bsmsvc.application.service.PaymentGatewayResolver}. Implementations
 * typically wrap a provider SDK client, which must itself be safe for concurrent use.
 */
public interface PaymentGatewayPort {

    CustomerResult createCustomer(CreateCustomerCommand command);

    void attachPaymentMethod(AttachPaymentMethodCommand command);

    void detachPaymentMethod(DetachPaymentMethodCommand command);

    SubscriptionResult createSubscription(CreateSubscriptionCommand command);

    SubscriptionResult updateSubscription(UpdateSubscriptionCommand command);

    void cancelSubscription(CancelSubscriptionCommand command);

    CheckoutSessionResult createCheckoutSession(CreateCheckoutSessionCommand command);

    ListPaymentMethodsResult listPaymentMethods(ListPaymentMethodsCommand command);

    PaymentIntentResult createPaymentIntent(CreatePaymentIntentCommand command);

    /** Off-session retry for dunning — confirms immediately with a specific payment method. */
    PaymentIntentResult retryPayment(RetryPaymentCommand command);

    /**
     * Retrieves the current status of a payment from the provider.
     * Used by reconciliation to refresh stale PENDING payments.
     */
    PaymentStatusResult retrievePaymentStatus(String externalId);

    RefundResult refund(RefundCommand command);
}
