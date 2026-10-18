package io.genfin.providerapi.port.health;

import io.genfin.api.port.spi.Extension;
import io.genfin.providerapi.health.ProviderHealth;

public interface HealthIndicator extends Extension {

  ProviderHealth check();
}
