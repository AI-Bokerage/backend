package io.genfin.providerapi.port.auth;

import io.genfin.api.port.spi.Extension;
import io.genfin.providerapi.auth.Credential;

/**
 * Supplies the {@link Credential} a gateway authenticates with — never exposes the provider SDK's
 * own auth types.
 */
public interface AuthenticationProvider extends Extension {

  Credential credential();
}
