package io.genfin.providerapi.internal.idempotency;

import io.genfin.payment.idempotency.IdempotencyKey;
import io.genfin.providerapi.port.idempotency.ProviderIdempotencyMapper;

/**
 * The common case: the provider accepts an arbitrary string idempotency key, so the mapping is
 * identity.
 */
public final class PassthroughIdempotencyMapper implements ProviderIdempotencyMapper {

  @Override
  public String toProviderKey(IdempotencyKey key) {
    return key.value();
  }
}
