package io.genfin.payment.port.authorization;

import io.genfin.api.port.spi.Extension;

/**
 * Whether an authorization must cover the full requested amount, or partial authorization is
 * acceptable.
 */
public interface AuthorizationStrategy extends Extension {

  boolean requiresFullAuthorization();
}
