package io.genfin.payment.port.idempotency;

import io.genfin.api.port.spi.Extension;
import io.genfin.payment.idempotency.IdempotencyKey;
import io.genfin.payment.idempotency.IdempotencyResult;
import java.util.Optional;

/**
 * Decides NEW/REPLAYED/CONFLICT for a key given the current request's fingerprint and whatever
 * fingerprint (if any) the application already had stored for that key.
 */
public interface IdempotencyValidator extends Extension {

  IdempotencyResult validate(
      IdempotencyKey key, String requestFingerprint, Optional<String> existingFingerprint);
}
