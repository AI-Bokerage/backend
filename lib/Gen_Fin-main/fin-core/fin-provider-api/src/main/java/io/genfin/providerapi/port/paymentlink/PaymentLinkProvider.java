package io.genfin.providerapi.port.paymentlink;

import io.genfin.api.port.spi.Extension;
import io.genfin.money.money.Money;
import io.genfin.payment.metadata.PaymentMetadata;
import io.genfin.providerapi.paymentlink.PaymentLink;
import java.time.Instant;

public interface PaymentLinkProvider extends Extension {

  PaymentLink create(Money amount, Instant expiresAt, PaymentMetadata metadata);

  PaymentLink retrieve(String id);

  void cancel(String id);
}
