package io.genfin.autoconfigure.provider;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.autoconfigure.GenFinExtensionRegistryAutoConfiguration;
import io.genfin.payment.port.gateway.PaymentGateway;
import io.genfin.providerapi.auth.Credential;
import io.genfin.providerapi.config.ProviderConfiguration;
import io.genfin.providerapi.descriptor.ProviderDescriptor;
import io.genfin.providerapi.descriptor.ProviderEnvironment;
import io.genfin.providerapi.descriptor.ProviderId;
import io.genfin.providerapi.descriptor.ProviderName;
import io.genfin.providerapi.descriptor.ProviderPriority;
import io.genfin.providerapi.descriptor.ProviderRegion;
import io.genfin.providerapi.descriptor.ProviderStatus;
import io.genfin.providerapi.descriptor.ProviderVersion;
import io.genfin.providerapi.spi.ProviderApiExtensions;
import io.genfin.razorpay.config.RazorpayConfiguration;
import io.genfin.razorpay.gateway.RazorpayGateway;
import io.genfin.stripe.config.StripeConfiguration;
import io.genfin.stripe.gateway.StripeGateway;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers every default fin-provider-api extension into the shared {@link ExtensionRegistry}.
 * Also builds a {@link PaymentGateway} bean for Stripe/Razorpay when the corresponding provider jar
 * is on the classpath and its API-key property is set — this is the mechanism behind "adding
 * fin-stripe as a dependency makes Spring discover it automatically." Neither provider module gains
 * a Spring dependency; this bean-construction code lives entirely here.
 */
@AutoConfiguration
@AutoConfigureAfter(GenFinExtensionRegistryAutoConfiguration.class)
@EnableConfigurationProperties({StripeProperties.class, RazorpayProperties.class})
public class ProviderAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "providerApiExtensionsRegistered")
  public Boolean providerApiExtensionsRegistered(ExtensionRegistry registry) {
    ProviderApiExtensions.registerDefaults(registry);
    return Boolean.TRUE;
  }

  @Bean
  @ConditionalOnClass(StripeGateway.class)
  @ConditionalOnProperty(prefix = "genfin.payment.providers.stripe", name = "api-key")
  @ConditionalOnMissingBean(name = "stripePaymentGateway")
  public PaymentGateway stripePaymentGateway(StripeProperties properties) {
    ProviderConfiguration base =
        ProviderConfiguration.builder()
            .descriptor(descriptorFor("stripe", "Stripe"))
            .credential(Credential.apiKey(properties.getApiKey()))
            .build();
    StripeConfiguration configuration = StripeConfiguration.of(base, properties.getWebhookSecret());
    return new StripeGateway(configuration);
  }

  @Bean
  @ConditionalOnClass(RazorpayGateway.class)
  @ConditionalOnProperty(prefix = "genfin.payment.providers.razorpay", name = "key-id")
  @ConditionalOnMissingBean(name = "razorpayPaymentGateway")
  public PaymentGateway razorpayPaymentGateway(RazorpayProperties properties) {
    ProviderConfiguration base =
        ProviderConfiguration.builder()
            .descriptor(descriptorFor("razorpay", "Razorpay"))
            .credential(Credential.apiKey(properties.getKeyId()))
            .build();
    RazorpayConfiguration configuration =
        RazorpayConfiguration.of(base, properties.getKeySecret(), properties.getWebhookSecret());
    return new RazorpayGateway(configuration);
  }

  private ProviderDescriptor descriptorFor(String id, String displayName) {
    return new ProviderDescriptor(
        ProviderId.of(id),
        new ProviderName(displayName),
        new ProviderVersion("1.0"),
        ProviderRegion.GLOBAL,
        ProviderEnvironment.PRODUCTION,
        ProviderPriority.DEFAULT,
        ProviderStatus.ACTIVE,
        null);
  }
}
