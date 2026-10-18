package io.genfin.providerapi.port.idempotency;

import io.genfin.api.port.spi.Extension;
import io.genfin.payment.idempotency.IdempotencyKey;

/**
 * Maps a Gen-Fin {@link IdempotencyKey} onto whatever idempotency mechanism a specific provider
 * expects.
 */
public interface ProviderIdempotencyMapper extends Extension {

  String toProviderKey(IdempotencyKey key);
}
