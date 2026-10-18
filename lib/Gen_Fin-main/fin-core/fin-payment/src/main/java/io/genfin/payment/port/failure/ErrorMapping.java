package io.genfin.payment.port.failure;

import io.genfin.api.port.spi.Extension;
import io.genfin.payment.failure.FailureReason;

/**
 * Translates a provider's own error code/message into a {@link FailureReason}. Implemented per
 * gateway, not here.
 */
public interface ErrorMapping extends Extension {

  FailureReason map(String providerErrorCode, String providerMessage);
}
