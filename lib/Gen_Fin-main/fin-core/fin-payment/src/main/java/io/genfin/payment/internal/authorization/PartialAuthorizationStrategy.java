package io.genfin.payment.internal.authorization;

import io.genfin.payment.port.authorization.AuthorizationStrategy;

public final class PartialAuthorizationStrategy implements AuthorizationStrategy {

  @Override
  public boolean requiresFullAuthorization() {
    return false;
  }
}
