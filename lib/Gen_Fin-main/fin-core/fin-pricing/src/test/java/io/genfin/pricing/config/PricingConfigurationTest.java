package io.genfin.pricing.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import io.genfin.pricing.calculation.PricingPolicies;
import io.genfin.pricing.catalog.CatalogRegistries;
import io.genfin.pricing.discount.DiscountPolicies;
import io.genfin.pricing.lifecycle.PricingLifecycles;
import io.genfin.pricing.tax.TaxExtensionPoint;
import io.genfin.pricing.validation.Validators;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the immutable, multi-piece {@link PricingConfiguration} builder and its {@link
 * PricingConfigurations#standard()} default.
 */
class PricingConfigurationTest {

  @Test
  void standardConfigurationCarriesAnEmptyButNonNullPolicySet() {
    PricingConfiguration configuration = PricingConfigurations.standard();

    assertThat(configuration.pricingPolicy()).isNotNull();
    assertThat(configuration.lifecycleProvider()).isNotNull();
    assertThat(configuration.taxPlaceholder()).isNotNull();
    assertThat(configuration.pricingValidator()).isNotNull();
    assertThat(configuration.catalogConfiguration()).isNotNull();
    assertThat(configuration.discountConfiguration()).isNotNull();
    assertThat(configuration.promotionConfiguration()).isNotNull();
    assertThat(configuration.couponConfiguration()).isNotNull();
    assertThat(configuration.creditConfiguration()).isNotNull();
    assertThat(configuration.quoteConfiguration()).isNotNull();
    assertThat(configuration.commercialRuleConfiguration()).isNotNull();
  }

  @Test
  void builderRejectsAnIncompleteConfiguration() {
    assertThatThrownBy(() -> PricingConfiguration.builder().build())
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void builderHonoursEveryExplicitlySuppliedCollaborator() {
    PricingConfiguration standard = PricingConfigurations.standard();
    DiscountConfiguration customDiscount =
        DiscountConfiguration.builder().discountPolicy(DiscountPolicies.empty()).build();

    PricingConfiguration configuration =
        PricingConfiguration.builder()
            .pricingPolicy(PricingPolicies.empty())
            .lifecycleProvider(PricingLifecycles.standard())
            .taxPlaceholder(TaxExtensionPoint.noOp())
            .pricingValidator(Validators.standard())
            .catalogConfiguration(
                CatalogConfiguration.builder().catalogRegistry(CatalogRegistries.empty()).build())
            .discountConfiguration(customDiscount)
            .promotionConfiguration(standard.promotionConfiguration())
            .couponConfiguration(standard.couponConfiguration())
            .creditConfiguration(standard.creditConfiguration())
            .quoteConfiguration(standard.quoteConfiguration())
            .commercialRuleConfiguration(standard.commercialRuleConfiguration())
            .build();

    assertThat(configuration.discountConfiguration()).isEqualTo(customDiscount);
  }
}
