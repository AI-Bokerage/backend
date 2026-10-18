package io.genfin.payment.method;

import io.genfin.payment.internal.method.DefaultPaymentMethodRegistry;
import io.genfin.payment.port.method.PaymentMethodProvider;
import io.genfin.payment.port.method.PaymentMethodRegistry;

public final class PaymentMethodRegistries {

  private PaymentMethodRegistries() {}

  public static PaymentMethodRegistry empty() {
    return new DefaultPaymentMethodRegistry();
  }

  public static PaymentMethodRegistry withProvider(PaymentMethodProvider provider) {
    PaymentMethodRegistry registry = new DefaultPaymentMethodRegistry();
    provider.provide().forEach(registry::register);
    return registry;
  }

  public static PaymentMethodProvider standardCatalog() {
    return () ->
        java.util.List.of(
            new PaymentMethodDescriptor(
                StandardPaymentMethodType.CARD, "Card", PaymentMethodCapabilities.fullyCapable()),
            new PaymentMethodDescriptor(
                StandardPaymentMethodType.BANK_TRANSFER,
                "Bank Transfer",
                PaymentMethodCapabilities.captureOnlyNoRefund()),
            new PaymentMethodDescriptor(
                StandardPaymentMethodType.UPI,
                "UPI",
                PaymentMethodCapabilities.captureOnlyNoRefund()),
            new PaymentMethodDescriptor(
                StandardPaymentMethodType.WALLET,
                "Wallet",
                PaymentMethodCapabilities.fullyCapable()),
            new PaymentMethodDescriptor(
                StandardPaymentMethodType.ACH,
                "ACH",
                PaymentMethodCapabilities.captureOnlyNoRefund()),
            new PaymentMethodDescriptor(
                StandardPaymentMethodType.SEPA,
                "SEPA",
                PaymentMethodCapabilities.captureOnlyNoRefund()),
            new PaymentMethodDescriptor(
                StandardPaymentMethodType.CASH,
                "Cash",
                PaymentMethodCapabilities.captureOnlyNoRefund()),
            new PaymentMethodDescriptor(
                StandardPaymentMethodType.CHEQUE,
                "Cheque",
                PaymentMethodCapabilities.captureOnlyNoRefund()),
            new PaymentMethodDescriptor(
                StandardPaymentMethodType.CRYPTO,
                "Crypto",
                PaymentMethodCapabilities.captureOnlyNoRefund()),
            new PaymentMethodDescriptor(
                StandardPaymentMethodType.INTERNAL_CREDIT,
                "Internal Credit",
                PaymentMethodCapabilities.fullyCapable()),
            new PaymentMethodDescriptor(
                StandardPaymentMethodType.GIFT_CARD,
                "Gift Card",
                PaymentMethodCapabilities.fullyCapable()));
  }
}
