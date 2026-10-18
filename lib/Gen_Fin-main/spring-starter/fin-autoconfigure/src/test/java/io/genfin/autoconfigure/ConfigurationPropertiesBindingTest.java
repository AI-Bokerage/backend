package io.genfin.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.autoconfigure.document.DocumentAutoConfiguration;
import io.genfin.autoconfigure.document.DocumentProperties;
import io.genfin.autoconfigure.dunning.DunningAutoConfiguration;
import io.genfin.autoconfigure.dunning.DunningProperties;
import io.genfin.autoconfigure.invoice.InvoiceAutoConfiguration;
import io.genfin.autoconfigure.invoice.InvoiceProperties;
import io.genfin.autoconfigure.payment.PaymentAutoConfiguration;
import io.genfin.autoconfigure.payment.PaymentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Confirms YAML/property values actually bind to the fields each XProperties class declares. */
class ConfigurationPropertiesBindingTest {

  private final ApplicationContextRunner base =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(GenFinExtensionRegistryAutoConfiguration.class));

  @Test
  void documentRendererBinds() {
    base.withConfiguration(AutoConfigurations.of(DocumentAutoConfiguration.class))
        .withPropertyValues("genfin.document.renderer=pdf")
        .run(
            context ->
                assertThat(context.getBean(DocumentProperties.class).getRenderer())
                    .isEqualTo("pdf"));
  }

  @Test
  void invoiceNumberingBindsWithSequentialTemplate() {
    base.withConfiguration(AutoConfigurations.of(InvoiceAutoConfiguration.class))
        .withPropertyValues(
            "genfin.invoice.numbering-strategy=sequential",
            "genfin.invoice.numbering-sequential-template=INV-{SEQ}")
        .run(
            context -> {
              InvoiceProperties properties = context.getBean(InvoiceProperties.class);
              assertThat(properties.getNumberingStrategy()).isEqualTo("sequential");
              assertThat(properties.getNumberingSequentialTemplate()).isEqualTo("INV-{SEQ}");
            });
  }

  @Test
  void paymentDefaultProviderBinds() {
    base.withConfiguration(AutoConfigurations.of(PaymentAutoConfiguration.class))
        .withPropertyValues("genfin.payment.default-provider=razorpay")
        .run(
            context ->
                assertThat(context.getBean(PaymentProperties.class).getDefaultProvider())
                    .isEqualTo("razorpay"));
  }

  @Test
  void dunningEnabledDefaultsToTrue() {
    // genfin.dunning.enabled also gates whether DunningAutoConfiguration activates at all
    // (see ModuleExtensionsRegisteredTest.dunningSkippedWhenDisabled) — so binding is only
    // observable in the enabled case.
    base.withConfiguration(AutoConfigurations.of(DunningAutoConfiguration.class))
        .run(context -> assertThat(context.getBean(DunningProperties.class).isEnabled()).isTrue());
  }
}
