package io.genfin.providerapi.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.payment.gateway.GatewayCapabilities;
import io.genfin.payment.gateway.GatewayHealth;
import io.genfin.payment.gateway.PaymentProvider;
import io.genfin.payment.port.gateway.PaymentGateway;
import org.junit.jupiter.api.Test;

/**
 * The one compliance suite every {@link PaymentGateway} implementation runs against — proving
 * behavioral consistency across providers for the parts testable without live credentials (the
 * network-calling {@code authorize}/{@code capture}/{@code refund} methods need a real sandbox
 * account and are each provider module's own concern, not this suite's).
 */
public abstract class PaymentGatewayComplianceTest {

  protected abstract PaymentGateway gateway();

  @Test
  void capabilitiesAreNeverNull() {
    GatewayCapabilities capabilities = gateway().capabilities();

    assertThat(capabilities).isNotNull();
    assertThat(capabilities.supportedMethods()).isNotNull();
  }

  @Test
  void capabilitiesDeclareAtLeastOneSupportedMethod() {
    assertThat(gateway().capabilities().supportedMethods()).isNotEmpty();
  }

  @Test
  void providerDescriptorHasNonBlankIdentity() {
    PaymentProvider provider = gateway().provider();

    assertThat(provider.providerId()).isNotBlank();
    assertThat(provider.displayName()).isNotBlank();
    assertThat(provider.capabilities()).isNotNull();
  }

  @Test
  void providerCapabilitiesDeclareAtLeastOneSupportedCurrencyAndCountry() {
    var capabilities = gateway().provider().capabilities();

    assertThat(capabilities.supportedCurrencyCodes()).isNotEmpty();
    assertThat(capabilities.supportedCountryCodes()).isNotEmpty();
  }

  @Test
  void healthCheckReportsAStatusWithoutThrowing() {
    GatewayHealth health = gateway().health();

    assertThat(health).isNotNull();
    assertThat(health.checkedAt()).isNotNull();
  }
}
