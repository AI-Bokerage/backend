package io.genfin.autoconfigure.payment;

import io.genfin.payment.gateway.GatewayRegistries;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code defaultProvider} is nullable: fin-payment has no default-by-name concept, so when unset
 * {@link GatewayRegistries#firstCapable()} is used directly, exactly as fin-payment's own {@code
 * PaymentExtensions} does.
 */
@ConfigurationProperties(prefix = "genfin.payment")
public class PaymentProperties {

  private String defaultProvider;

  public String getDefaultProvider() {
    return defaultProvider;
  }

  public void setDefaultProvider(String defaultProvider) {
    this.defaultProvider = defaultProvider;
  }
}
