package io.genfin.providerapi.port.tokenization;

import io.genfin.api.port.spi.Extension;
import io.genfin.payment.method.PaymentMethod;
import io.genfin.providerapi.tokenization.PaymentToken;

/**
 * Turns an already-masked {@link PaymentMethod} into a provider-generated token reference. Never
 * accepts raw card/account data — the engine models tokenization generically, providers perform it.
 */
public interface TokenizationProvider extends Extension {

  PaymentToken tokenize(PaymentMethod method);
}
