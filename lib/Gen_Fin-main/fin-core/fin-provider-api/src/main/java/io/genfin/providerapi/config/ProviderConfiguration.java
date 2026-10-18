package io.genfin.providerapi.config;

import io.genfin.api.validation.Validate;
import io.genfin.providerapi.auth.Credential;
import io.genfin.providerapi.capability.CapabilitySet;
import io.genfin.providerapi.descriptor.ProviderDescriptor;
import io.genfin.providerapi.port.retry.RetryPolicy;
import io.genfin.providerapi.retry.RetryPolicies;
import java.time.Duration;

/**
 * The base configuration every provider module builds on. {@code fin-stripe}/{@code fin-razorpay}
 * compose this with their own provider-specific fields (webhook secrets, API versions, ...) rather
 * than subclassing a plain Java value type.
 */
public final class ProviderConfiguration {

  private final ProviderDescriptor descriptor;
  private final Credential credential;
  private final Duration timeout;
  private final RetryPolicy retryPolicy;
  private final CapabilitySet capabilities;

  private ProviderConfiguration(Builder builder) {
    this.descriptor = Validate.notNull(builder.descriptor, "descriptor must not be null.");
    this.credential = Validate.notNull(builder.credential, "credential must not be null.");
    this.timeout = Validate.notNull(builder.timeout, "timeout must not be null.");
    this.retryPolicy = Validate.notNull(builder.retryPolicy, "retryPolicy must not be null.");
    this.capabilities = Validate.notNull(builder.capabilities, "capabilities must not be null.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public ProviderDescriptor descriptor() {
    return descriptor;
  }

  public Credential credential() {
    return credential;
  }

  public Duration timeout() {
    return timeout;
  }

  public RetryPolicy retryPolicy() {
    return retryPolicy;
  }

  public CapabilitySet capabilities() {
    return capabilities;
  }

  public static final class Builder {

    private ProviderDescriptor descriptor;
    private Credential credential;
    private Duration timeout = Duration.ofSeconds(30);
    private RetryPolicy retryPolicy = RetryPolicies.standard();
    private CapabilitySet capabilities = new CapabilitySet(java.util.Set.of());

    public Builder descriptor(ProviderDescriptor descriptor) {
      this.descriptor = descriptor;
      return this;
    }

    public Builder credential(Credential credential) {
      this.credential = credential;
      return this;
    }

    public Builder timeout(Duration timeout) {
      this.timeout = timeout;
      return this;
    }

    public Builder retryPolicy(RetryPolicy retryPolicy) {
      this.retryPolicy = retryPolicy;
      return this;
    }

    public Builder capabilities(CapabilitySet capabilities) {
      this.capabilities = capabilities;
      return this;
    }

    public ProviderConfiguration build() {
      return new ProviderConfiguration(this);
    }
  }
}
