package io.genfin.providerapi.port.checkout;

import io.genfin.api.port.spi.Extension;
import io.genfin.money.money.Money;
import io.genfin.payment.metadata.PaymentMetadata;
import io.genfin.providerapi.checkout.CheckoutMode;
import io.genfin.providerapi.checkout.CheckoutSession;

public interface CheckoutProvider extends Extension {

  CheckoutSession createSession(CheckoutMode mode, Money amount, PaymentMetadata metadata);
}
