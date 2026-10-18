package io.genfin.providerapi.auth;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import java.util.Map;

/**
 * A generic credential — never a provider SDK auth type. {@code attributes} carries whatever the
 * {@code scheme} needs (e.g. {@code "apiKey"} for {@link AuthenticationScheme#API_KEY}, {@code
 * "username"}/{@code "password"} for {@link AuthenticationScheme#BASIC_AUTH}).
 */
public record Credential(AuthenticationScheme scheme, Map<String, String> attributes)
    implements ValueObject {

  public Credential {
    Validate.notNull(scheme, "scheme must not be null.");
    attributes = Map.copyOf(attributes);
  }

  public static Credential apiKey(String apiKey) {
    return new Credential(AuthenticationScheme.API_KEY, Map.of("apiKey", apiKey));
  }

  public static Credential bearerToken(String token) {
    return new Credential(AuthenticationScheme.BEARER_TOKEN, Map.of("token", token));
  }

  public static Credential basicAuth(String username, String password) {
    return new Credential(
        AuthenticationScheme.BASIC_AUTH, Map.of("username", username, "password", password));
  }

  public static Credential hmac(String secret) {
    return new Credential(AuthenticationScheme.HMAC, Map.of("secret", secret));
  }
}
