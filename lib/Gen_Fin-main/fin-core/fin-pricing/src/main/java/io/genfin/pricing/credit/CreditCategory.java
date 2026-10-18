package io.genfin.pricing.credit;

/**
 * The *kind* of a {@link Credit} grant sitting in a {@link CreditWallet} (e.g. what an application
 * might call "Wallet", "Gift", "Promotional" or "Account" credit). Not a closed enum - Gen-Fin
 * defines no business credit categories of its own; applications implement this interface for their
 * own taxonomy. Mirrors {@code io.genfin.ledger.account.AccountType}'s and {@code
 * io.genfin.pricing.catalog.CatalogCategory}'s extensible-taxonomy pattern.
 */
public interface CreditCategory {

  String code();
}
