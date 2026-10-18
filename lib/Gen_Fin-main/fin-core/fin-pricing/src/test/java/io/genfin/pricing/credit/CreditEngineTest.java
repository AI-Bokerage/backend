package io.genfin.pricing.credit;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import io.genfin.pricing.id.CatalogId;
import io.genfin.pricing.id.PricingRequestId;
import io.genfin.pricing.port.credit.CreditPolicy;
import io.genfin.pricing.port.credit.CreditRegistry;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.price.PriceBreakdown;
import io.genfin.pricing.price.PriceComponent;
import io.genfin.pricing.price.PriceType;
import io.genfin.pricing.pricing.PricingAttributes;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Exercises the default {@link CreditPolicy} (via {@link CreditPolicies}/{@link CreditStrategies})
 * and the default {@link CreditValidator} (via {@link CreditValidators}): a resolved wallet is only
 * drawn against when registered and funded, at most once and capped at the running amount, and a
 * draw against an already-exhausted wallet is flagged rather than silently accepted.
 */
class CreditEngineTest {

  private static final Currency USD =
      CurrencyFactory.newCurrency().code("USD").symbol("$").displayName("US Dollar").build();

  private static final CreditCategory WALLET = () -> "WALLET";
  private static final CreditCategory GIFT = () -> "GIFT";

  @Test
  void drawsDownAFundedWalletCappedAtTheRunningAmount() {
    CreditRegistry registry = CreditRegistries.empty();
    WalletId walletId = WalletId.of("customer-1");
    registry.register(CreditWallet.of(walletId, List.of(Credit.of(WALLET, Money.of(30, USD)))));
    CreditPolicy policy = CreditPolicies.of(registry, List.of(CreditStrategies.fromAttribute()));

    PricingRequest.Line line = line();
    PricingContext context = contextWithWallet(line, walletId.value());

    List<CreditResult> results = policy.apply(List.of(basePrice()), List.of(line), context);

    assertThat(results.get(0).hasCredit()).isTrue();
    assertThat(results.get(0).price().amount()).isEqualTo(Money.of(70, USD));
    assertThat(results.get(0).allocation().orElseThrow().amount()).isEqualTo(Money.of(30, USD));
    assertThat(results.get(0).allocation().orElseThrow().byCategory().get("WALLET"))
        .isEqualTo(Money.of(30, USD));
  }

  @Test
  void capsTheDrawAtTheRunningAmountWhenTheBalanceIsLarger() {
    CreditRegistry registry = CreditRegistries.empty();
    WalletId walletId = WalletId.of("customer-2");
    registry.register(CreditWallet.of(walletId, List.of(Credit.of(WALLET, Money.of(500, USD)))));
    CreditPolicy policy = CreditPolicies.of(registry, List.of(CreditStrategies.fromAttribute()));

    PricingRequest.Line line = line();
    List<CreditResult> results =
        policy.apply(
            List.of(basePrice()), List.of(line), contextWithWallet(line, walletId.value()));

    assertThat(results.get(0).price().amount()).isEqualTo(Money.zero(USD));
    assertThat(results.get(0).allocation().orElseThrow().amount()).isEqualTo(Money.of(100, USD));
  }

  @Test
  void apportionsTheDrawAcrossCategoriesInGrantOrder() {
    CreditRegistry registry = CreditRegistries.empty();
    WalletId walletId = WalletId.of("customer-3");
    registry.register(
        CreditWallet.of(
            walletId,
            List.of(Credit.of(GIFT, Money.of(40, USD)), Credit.of(WALLET, Money.of(40, USD)))));
    CreditPolicy policy = CreditPolicies.of(registry, List.of(CreditStrategies.fromAttribute()));

    PricingRequest.Line line = line();
    List<CreditResult> results =
        policy.apply(
            List.of(basePrice()), List.of(line), contextWithWallet(line, walletId.value()));

    CreditAllocation allocation = results.get(0).allocation().orElseThrow();
    assertThat(allocation.amount()).isEqualTo(Money.of(80, USD));
    assertThat(allocation.byCategory().get("GIFT")).isEqualTo(Money.of(40, USD));
    assertThat(allocation.byCategory().get("WALLET")).isEqualTo(Money.of(40, USD));
  }

  @Test
  void leavesThePriceUnchangedWhenNoWalletIsResolved() {
    CreditRegistry registry = CreditRegistries.empty();
    CreditPolicy policy = CreditPolicies.of(registry, List.of(CreditStrategies.fromAttribute()));

    PricingRequest.Line line = line();
    List<CreditResult> results =
        policy.apply(List.of(basePrice()), List.of(line), contextFor(line));

    assertThat(results.get(0).hasCredit()).isFalse();
  }

  @Test
  void leavesThePriceUnchangedWhenTheResolvedWalletIsUnregisteredOrEmpty() {
    CreditRegistry registry = CreditRegistries.empty();
    registry.register(CreditWallet.empty(WalletId.of("customer-4")));
    CreditPolicy policy = CreditPolicies.of(registry, List.of(CreditStrategies.fromAttribute()));

    PricingRequest.Line line = line();
    List<CreditResult> results =
        policy.apply(List.of(basePrice()), List.of(line), contextWithWallet(line, "customer-4"));

    assertThat(results.get(0).hasCredit()).isFalse();
  }

  @Test
  void validatorFlagsADrawAgainstAnAlreadyExhaustedWallet() {
    CreditRegistry registry = CreditRegistries.empty();
    WalletId walletId = WalletId.of("customer-5");
    CreditWallet wallet = CreditWallet.of(walletId, List.of(Credit.of(WALLET, Money.of(20, USD))));
    registry.register(wallet);
    registry.recordAllocation(
        CreditAllocation.of(
            walletId,
            Money.of(20, USD),
            java.util.Map.of("WALLET", Money.of(20, USD)),
            PricingRequestId.generate()));

    CreditValidator validator = CreditValidators.standard(registry);
    CreditResult result =
        CreditResult.applied(
            basePrice(),
            CreditAllocation.of(
                walletId,
                Money.of(5, USD),
                java.util.Map.of("WALLET", Money.of(5, USD)),
                PricingRequestId.generate()));

    List<io.genfin.pricing.calculation.CalculationIssue> issues =
        validator.validate(result, contextFor(line()));

    assertThat(issues).anyMatch(issue -> "credit-wallet-exhausted".equals(issue.ruleCode()));
  }

  private static Price basePrice() {
    return new Price(
        CatalogId.generate(),
        PriceBreakdown.of(PriceComponent.of(PriceType.BASE, "base", Money.of(100, USD))));
  }

  private static PricingRequest.Line line() {
    return new PricingRequest.Line(CatalogId.generate(), 1);
  }

  private static PricingContext contextFor(PricingRequest.Line line) {
    return PricingContext.start(
        PricingRequestId.generate(), List.of(line), PricingAttributes.empty());
  }

  private static PricingContext contextWithWallet(PricingRequest.Line line, String walletId) {
    return PricingContext.start(
        PricingRequestId.generate(),
        List.of(line),
        PricingAttributes.empty().with(WalletId.ATTRIBUTE_KEY, walletId));
  }
}
