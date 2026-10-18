package io.genfin.autoconfigure.dunning;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code enabled} gates whether {@link DunningAutoConfiguration} runs at all — fin-dunning itself
 * has no such field; this is a Spring-only convention for turning the whole module's wiring off.
 */
@ConfigurationProperties(prefix = "genfin.dunning")
public class DunningProperties {

  private boolean enabled = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
