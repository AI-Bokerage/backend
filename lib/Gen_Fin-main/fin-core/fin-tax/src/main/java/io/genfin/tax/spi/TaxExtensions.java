package io.genfin.tax.spi;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.money.port.tax.TaxCalculator;
import io.genfin.tax.internal.decision.DefaultDomesticPolicy;
import io.genfin.tax.internal.decision.DefaultExportPolicy;
import io.genfin.tax.internal.decision.DefaultImportPolicy;
import io.genfin.tax.internal.decision.DefaultRegistrationPolicy;
import io.genfin.tax.port.DomesticPolicy;
import io.genfin.tax.port.ExportPolicy;
import io.genfin.tax.port.ImportPolicy;
import io.genfin.tax.port.RegistrationPolicy;
import io.genfin.tax.port.TaxDecisionResolver;
import io.genfin.tax.port.TaxDecisionResolvers;
import io.genfin.tax.port.TaxEngines;
import io.genfin.tax.port.TaxRateProvider;
import io.genfin.tax.port.TaxRateProviders;

/**
 * Registers every default Tax Engine V1 extension so downstream code discovers them through one
 * mechanism. Mirrors {@code io.genfin.money.spi.MoneyExtensions}.
 *
 * <p><strong>Registration order matters:</strong> this registers {@link TaxCalculator} — {@code
 * fin-money}'s own extension point — with {@link TaxEngines#standard()}. Since {@code
 * ExtensionRegistry#find} returns the <em>first-registered</em> implementation for a type, call
 * this method <strong>before</strong> {@code MoneyExtensions.registerDefaults(registry)} so the Tax
 * Engine wins over {@code fin-money}'s zero-config {@code NoOpTaxCalculator}:
 *
 * <pre>{@code
 * ExtensionRegistry registry = ExtensionRegistries.create();
 * TaxExtensions.registerDefaults(registry);   // registers TaxCalculator first
 * MoneyExtensions.registerDefaults(registry); // its own TaxCalculator default now loses
 * }</pre>
 */
public final class TaxExtensions {

  private TaxExtensions() {}

  public static void registerDefaults(ExtensionRegistry registry) {
    registry.register(RegistrationPolicy.class, new DefaultRegistrationPolicy());
    registry.register(DomesticPolicy.class, new DefaultDomesticPolicy());

    ExportPolicy exportPolicy =
        new DefaultExportPolicy(
            io.genfin.tax.api.config.IndianTaxConfiguration.standard().defaultExportTreatment());
    registry.register(ExportPolicy.class, exportPolicy);
    registry.register(ImportPolicy.class, new DefaultImportPolicy());

    registry.register(TaxDecisionResolver.class, TaxDecisionResolvers.standard());
    registry.register(TaxRateProvider.class, TaxRateProviders.standard());

    registry.register(TaxCalculator.class, TaxEngines.standard());
  }
}
