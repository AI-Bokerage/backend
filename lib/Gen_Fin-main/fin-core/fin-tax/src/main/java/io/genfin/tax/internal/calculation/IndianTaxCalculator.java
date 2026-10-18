package io.genfin.tax.internal.calculation;

import io.genfin.api.exception.GenFinException;
import io.genfin.api.result.Result;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.money.port.tax.TaxCalculator;
import io.genfin.money.tax.TaxBreakdown;
import io.genfin.money.tax.TaxComputationResult;
import io.genfin.money.tax.TaxContext;
import io.genfin.money.tax.TaxMetadata;
import io.genfin.tax.api.decision.TaxComponentType;
import io.genfin.tax.api.decision.TaxDecision;
import io.genfin.tax.api.exception.MissingTaxProfileException;
import io.genfin.tax.api.exception.UnsupportedTransactionException;
import io.genfin.tax.api.metadata.TaxMetadataKeys;
import io.genfin.tax.api.party.PartyTaxProfile;
import io.genfin.tax.api.party.StandardCountryCode;
import io.genfin.tax.api.party.StandardRegistrationType;
import io.genfin.tax.api.party.StateCode;
import io.genfin.tax.api.rate.TaxRate;
import io.genfin.tax.api.transaction.TransactionContext;
import io.genfin.tax.port.TaxDecisionResolver;
import io.genfin.tax.port.TaxRateProvider;
import java.util.ArrayList;
import java.util.List;

/**
 * Bridges Tax Engine V1 into {@code fin-money}'s existing {@link TaxCalculator} extension point.
 * The seller/buyer {@link PartyTaxProfile}s the resolver needs are read out of {@link
 * TaxContext#metadata()} via {@link TaxMetadataKeys} — {@code TaxContext} itself is never changed,
 * so this calculator is a pure additive implementation of an existing public interface.
 */
public final class IndianTaxCalculator implements TaxCalculator {

  private final TaxDecisionResolver resolver;
  private final TaxRateProvider rateProvider;

  public IndianTaxCalculator(TaxDecisionResolver resolver, TaxRateProvider rateProvider) {
    this.resolver = Validate.notNull(resolver, "resolver must not be null.");
    this.rateProvider = Validate.notNull(rateProvider, "rateProvider must not be null.");
  }

  @Override
  public Result<TaxComputationResult> calculate(TaxContext context) {
    Validate.notNull(context, "context must not be null.");
    try {
      PartyTaxProfile seller =
          profileFrom(
              context.metadata(),
              "seller",
              TaxMetadataKeys.SELLER_COUNTRY,
              TaxMetadataKeys.SELLER_STATE,
              TaxMetadataKeys.SELLER_REGISTRATION_TYPE,
              TaxMetadataKeys.SELLER_GST_NUMBER,
              TaxMetadataKeys.SELLER_LUT_NUMBER);
      PartyTaxProfile buyer =
          profileFrom(
              context.metadata(),
              "buyer",
              TaxMetadataKeys.BUYER_COUNTRY,
              TaxMetadataKeys.BUYER_STATE,
              TaxMetadataKeys.BUYER_REGISTRATION_TYPE,
              TaxMetadataKeys.BUYER_GST_NUMBER,
              TaxMetadataKeys.BUYER_LUT_NUMBER);

      TaxDecision decision = resolver.resolve(seller, buyer, TransactionContext.at(context.asOf()));

      Money amount = context.taxableAmount().amount();
      List<io.genfin.money.tax.TaxComponent> lines = new ArrayList<>();
      for (TaxComponentType componentType : decision.treatment().components()) {
        TaxRate rate =
            rateProvider
                .rateFor(componentType)
                .orElseThrow(
                    () ->
                        new UnsupportedTransactionException(
                            "No TaxRate configured for component: " + componentType.code()));
        Money componentAmount = amount.multiply(rate.rate().fraction());
        lines.add(
            new io.genfin.money.tax.TaxComponent(
                componentType.code(), rate.rate(), componentAmount));
      }

      TaxBreakdown breakdown = new TaxBreakdown(lines, amount.currency());
      return Result.success(new TaxComputationResult(context, breakdown));
    } catch (GenFinException e) {
      return Result.failure(e.errorCode(), e.getMessage(), e);
    }
  }

  private PartyTaxProfile profileFrom(
      TaxMetadata metadata,
      String partyLabel,
      String countryKey,
      String stateKey,
      String registrationTypeKey,
      String gstNumberKey,
      String lutNumberKey) {
    String countryCode =
        metadata.find(countryKey).orElseThrow(() -> new MissingTaxProfileException(partyLabel));
    String registrationTypeCode =
        metadata
            .find(registrationTypeKey)
            .orElseThrow(() -> new MissingTaxProfileException(partyLabel));
    StateCode state = metadata.find(stateKey).map(StateCode::of).orElse(null);
    String gstNumber = metadata.find(gstNumberKey).orElse(null);
    String lutNumber = metadata.find(lutNumberKey).orElse(null);

    return new PartyTaxProfile(
        StandardCountryCode.of(countryCode),
        state,
        StandardRegistrationType.of(registrationTypeCode),
        gstNumber,
        lutNumber);
  }
}
