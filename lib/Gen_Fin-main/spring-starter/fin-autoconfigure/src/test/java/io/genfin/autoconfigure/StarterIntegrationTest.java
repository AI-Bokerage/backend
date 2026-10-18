package io.genfin.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.ledger.port.posting.PostingEngine;
import io.genfin.pricing.port.calculation.PricingEngine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Loads every autoconfiguration class Spring Boot would discover from {@code
 * AutoConfiguration.imports} in a real application (no Stripe/Razorpay properties set), with no
 * application code beyond an empty marker configuration — proving zero-config startup actually
 * works end to end, not just that the pieces compile.
 */
class StarterIntegrationTest {

  @Test
  void contextLoadsWithZeroConfiguration() {
    new ApplicationContextRunner()
        .withUserConfiguration(EmptyApplication.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(ExtensionRegistry.class);
              assertThat(context).hasSingleBean(PostingEngine.class);
              assertThat(context).hasSingleBean(PricingEngine.class);
            });
  }

  @Configuration
  @ImportAutoConfiguration({
    GenFinExtensionRegistryAutoConfiguration.class,
    io.genfin.autoconfigure.money.MoneyAutoConfiguration.class,
    io.genfin.autoconfigure.invoice.InvoiceAutoConfiguration.class,
    io.genfin.autoconfigure.payment.PaymentAutoConfiguration.class,
    io.genfin.autoconfigure.refund.RefundAutoConfiguration.class,
    io.genfin.autoconfigure.reconciliation.ReconciliationAutoConfiguration.class,
    io.genfin.autoconfigure.ledger.LedgerAutoConfiguration.class,
    io.genfin.autoconfigure.pricing.PricingAutoConfiguration.class,
    io.genfin.autoconfigure.dunning.DunningAutoConfiguration.class,
    io.genfin.autoconfigure.document.DocumentAutoConfiguration.class,
    io.genfin.autoconfigure.provider.ProviderAutoConfiguration.class,
    io.genfin.autoconfigure.health.HealthAutoConfiguration.class
  })
  static class EmptyApplication {}
}
