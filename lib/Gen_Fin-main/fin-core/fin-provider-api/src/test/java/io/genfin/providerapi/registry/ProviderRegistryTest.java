package io.genfin.providerapi.registry;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.money.Money;
import io.genfin.payment.gateway.GatewayRequest;
import io.genfin.payment.idempotency.IdempotencyKey;
import io.genfin.payment.method.MethodBuilder;
import io.genfin.payment.method.StandardPaymentMethodType;
import io.genfin.providerapi.descriptor.ProviderDescriptor;
import io.genfin.providerapi.descriptor.ProviderEnvironment;
import io.genfin.providerapi.descriptor.ProviderId;
import io.genfin.providerapi.descriptor.ProviderName;
import io.genfin.providerapi.descriptor.ProviderPriority;
import io.genfin.providerapi.descriptor.ProviderRegion;
import io.genfin.providerapi.descriptor.ProviderStatus;
import io.genfin.providerapi.descriptor.ProviderVersion;
import io.genfin.providerapi.port.registry.ProviderRegistry;
import io.genfin.providerapi.port.registry.ProviderResolver;
import io.genfin.providerapi.support.FakePaymentGateway;
import io.genfin.providerapi.support.TestCurrencies;
import org.junit.jupiter.api.Test;

class ProviderRegistryTest {

  private static ProviderDescriptor descriptor(String id, ProviderStatus status, int priority) {
    return new ProviderDescriptor(
        ProviderId.of(id),
        new ProviderName(id),
        new ProviderVersion("1.0"),
        ProviderRegion.GLOBAL,
        ProviderEnvironment.SANDBOX,
        new ProviderPriority(priority),
        status,
        null);
  }

  @Test
  void registryStoresDescriptorAndGatewayTogether() {
    ProviderRegistry registry = ProviderRegistries.empty();
    registry.register(
        descriptor("stripe", ProviderStatus.ACTIVE, 0), new FakePaymentGateway("stripe"));

    assertThat(registry.findDescriptor(ProviderId.of("stripe"))).isPresent();
    assertThat(registry.findGateway(ProviderId.of("stripe"))).isPresent();
    assertThat(registry.findAll()).hasSize(1);
  }

  @Test
  void selectorPicksHighestPriorityUsableProvider() {
    ProviderRegistry registry = ProviderRegistries.empty();
    registry.register(descriptor("low", ProviderStatus.ACTIVE, 1), new FakePaymentGateway("low"));
    registry.register(
        descriptor("high", ProviderStatus.ACTIVE, 10), new FakePaymentGateway("high"));
    registry.register(
        descriptor("disabled", ProviderStatus.DISABLED, 100), new FakePaymentGateway("disabled"));

    var selected = ProviderRegistries.highestPriority().select(registry.findAll());

    assertThat(selected).isPresent();
    assertThat(selected.get().id()).isEqualTo(ProviderId.of("high"));
  }

  @Test
  void resolverCombinesSelectionAndRegistryLookup() {
    ProviderRegistry registry = ProviderRegistries.empty();
    registry.register(
        descriptor("stripe", ProviderStatus.ACTIVE, 5), new FakePaymentGateway("stripe"));

    ProviderResolver resolver = ProviderRegistries.standardResolver();
    GatewayRequest request =
        new GatewayRequest(
            Money.of("10.00", TestCurrencies.USD),
            MethodBuilder.newMethod()
                .type(StandardPaymentMethodType.CARD)
                .maskedIdentifier("x")
                .build(),
            null,
            IdempotencyKey.of("k1"),
            null);

    assertThat(resolver.resolve(request, registry)).isPresent();
  }

  @Test
  void noUsableProviderResolvesToEmpty() {
    ProviderRegistry registry = ProviderRegistries.empty();
    registry.register(
        descriptor("stripe", ProviderStatus.DISABLED, 5), new FakePaymentGateway("stripe"));

    assertThat(ProviderRegistries.highestPriority().select(registry.findAll())).isEmpty();
  }
}
