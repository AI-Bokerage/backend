package io.genfin.providerapi.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CredentialTest {

  @Test
  void apiKeyFactoryStoresApiKeyAttribute() {
    Credential credential = Credential.apiKey("sk_test_123");

    assertThat(credential.scheme()).isEqualTo(AuthenticationScheme.API_KEY);
    assertThat(credential.attributes()).containsEntry("apiKey", "sk_test_123");
  }

  @Test
  void basicAuthFactoryStoresUsernameAndPassword() {
    Credential credential = Credential.basicAuth("user", "pass");

    assertThat(credential.attributes())
        .containsEntry("username", "user")
        .containsEntry("password", "pass");
  }

  @Test
  void hmacFactoryStoresSecret() {
    assertThat(Credential.hmac("whsec_abc").scheme()).isEqualTo(AuthenticationScheme.HMAC);
  }
}
