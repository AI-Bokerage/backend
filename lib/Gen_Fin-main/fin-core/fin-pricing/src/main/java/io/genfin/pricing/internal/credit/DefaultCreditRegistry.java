package io.genfin.pricing.internal.credit;

import io.genfin.api.exception.ValidationException;
import io.genfin.money.currency.Currency;
import io.genfin.money.money.Money;
import io.genfin.pricing.credit.CreditAllocation;
import io.genfin.pricing.credit.CreditUsage;
import io.genfin.pricing.credit.CreditWallet;
import io.genfin.pricing.credit.WalletId;
import io.genfin.pricing.port.credit.CreditRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class DefaultCreditRegistry implements CreditRegistry {

  private final ConcurrentMap<String, CreditWallet> wallets = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Money> consumed = new ConcurrentHashMap<>();

  @Override
  public void register(CreditWallet wallet) {
    wallets.put(wallet.id().value(), wallet);
  }

  @Override
  public Optional<CreditWallet> find(WalletId id) {
    return Optional.ofNullable(wallets.get(id.value()));
  }

  @Override
  public CreditUsage usageOf(WalletId id) {
    CreditWallet wallet = require(id);
    Money consumedSoFar = consumed.get(id.value());
    Currency currency = currencyOf(wallet, consumedSoFar, id);
    Money limit = wallet.balanceAt(Instant.now(), currency).total();
    Money used = consumedSoFar == null ? Money.zero(currency) : consumedSoFar;
    return new CreditUsage(id, used, limit);
  }

  @Override
  public void recordAllocation(CreditAllocation allocation) {
    consumed.merge(allocation.walletId().value(), allocation.amount(), Money::add);
  }

  @Override
  public List<CreditWallet> findAll() {
    return List.copyOf(wallets.values());
  }

  private static Currency currencyOf(CreditWallet wallet, Money consumedSoFar, WalletId id) {
    if (!wallet.credits().isEmpty()) {
      return wallet.credits().get(0).amount().currency();
    }
    if (consumedSoFar != null) {
      return consumedSoFar.currency();
    }
    throw new ValidationException(
        "Cannot determine currency for empty credit wallet: " + id.value());
  }
}
