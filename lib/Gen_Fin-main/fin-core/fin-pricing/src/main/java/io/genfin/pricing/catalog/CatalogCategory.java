package io.genfin.pricing.catalog;

/**
 * The *kind* of a {@link CatalogItem} (e.g. what an application might call "Subscription" or
 * "One-Time Fee"). Not a closed enum - Gen-Fin defines no business catalog categories of its own;
 * applications implement this interface for their own taxonomy. Mirrors {@code
 * io.genfin.ledger.account.AccountType}'s extensible-taxonomy pattern.
 */
public interface CatalogCategory {

  String code();
}
