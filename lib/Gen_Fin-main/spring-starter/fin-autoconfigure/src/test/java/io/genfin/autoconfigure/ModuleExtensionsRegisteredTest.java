package io.genfin.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.autoconfigure.dunning.DunningAutoConfiguration;
import io.genfin.autoconfigure.invoice.InvoiceAutoConfiguration;
import io.genfin.autoconfigure.ledger.LedgerAutoConfiguration;
import io.genfin.autoconfigure.money.MoneyAutoConfiguration;
import io.genfin.autoconfigure.pricing.PricingAutoConfiguration;
import io.genfin.autoconfigure.reconciliation.ReconciliationAutoConfiguration;
import io.genfin.autoconfigure.refund.RefundAutoConfiguration;
import io.genfin.invoice.port.numbering.InvoiceNumberGenerator;
import io.genfin.ledger.port.posting.PostingEngine;
import io.genfin.money.port.currency.CurrencyProvider;
import io.genfin.pricing.port.calculation.PricingEngine;
import io.genfin.reconciliation.port.matching.MatchingEngine;
import io.genfin.refund.port.calculation.RefundCalculator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * One test per module's {@code XAutoConfiguration}: confirms its defaults land in the shared {@link
 * ExtensionRegistry} (or, for fin-ledger/fin-pricing, that their one real facade bean is produced).
 * Every module's AutoConfiguration is mechanically identical (register defaults, done) except these
 * two facades and Money/Invoice/Payment/Document (covered in their own test files), so a
 * table-shaped test per module here avoids ten near-duplicate files.
 */
class ModuleExtensionsRegisteredTest {

  private final ApplicationContextRunner base =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(GenFinExtensionRegistryAutoConfiguration.class));

  @Test
  void refundDefaultsRegistered() {
    base.withConfiguration(AutoConfigurations.of(RefundAutoConfiguration.class))
        .run(
            context ->
                assertThat(context.getBean(ExtensionRegistry.class).find(RefundCalculator.class))
                    .isPresent());
  }

  @Test
  void reconciliationDefaultsRegistered() {
    base.withConfiguration(AutoConfigurations.of(ReconciliationAutoConfiguration.class))
        .run(
            context ->
                assertThat(context.getBean(ExtensionRegistry.class).find(MatchingEngine.class))
                    .isPresent());
  }

  @Test
  void invoiceDefaultsRegistered() {
    base.withConfiguration(AutoConfigurations.of(InvoiceAutoConfiguration.class))
        .run(
            context ->
                assertThat(
                        context.getBean(ExtensionRegistry.class).find(InvoiceNumberGenerator.class))
                    .isPresent());
  }

  @Test
  void moneyDefaultsRegistered() {
    base.withConfiguration(AutoConfigurations.of(MoneyAutoConfiguration.class))
        .run(
            context ->
                assertThat(context.getBean(ExtensionRegistry.class).find(CurrencyProvider.class))
                    .isPresent());
  }

  @Test
  void dunningDefaultsRegisteredWhenEnabled() {
    base.withConfiguration(AutoConfigurations.of(DunningAutoConfiguration.class))
        .run(context -> assertThat(context).hasBean("dunningExtensionsRegistered"));
  }

  @Test
  void dunningSkippedWhenDisabled() {
    base.withConfiguration(AutoConfigurations.of(DunningAutoConfiguration.class))
        .withPropertyValues("genfin.dunning.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean("dunningExtensionsRegistered"));
  }

  @Test
  void ledgerExposesPostingEngineFacade() {
    base.withConfiguration(AutoConfigurations.of(LedgerAutoConfiguration.class))
        .run(context -> assertThat(context).hasSingleBean(PostingEngine.class));
  }

  @Test
  void pricingExposesPricingEngineFacade() {
    base.withConfiguration(AutoConfigurations.of(PricingAutoConfiguration.class))
        .run(context -> assertThat(context).hasSingleBean(PricingEngine.class));
  }
}
