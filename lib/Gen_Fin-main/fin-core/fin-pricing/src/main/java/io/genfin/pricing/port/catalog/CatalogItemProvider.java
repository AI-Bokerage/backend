package io.genfin.pricing.port.catalog;

import io.genfin.pricing.catalog.CatalogItem;
import java.util.List;

/**
 * Supplies the {@link CatalogItem}s a deployment recognizes. Gen-Fin ships no default
 * implementation - an application's products and services are entirely its own to define and
 * register. Mirrors {@code io.genfin.ledger.port.account.AccountTypeProvider}.
 */
@FunctionalInterface
public interface CatalogItemProvider {

  List<CatalogItem> provide();
}
