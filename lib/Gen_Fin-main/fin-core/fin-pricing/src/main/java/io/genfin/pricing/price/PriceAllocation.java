package io.genfin.pricing.price;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.money.port.allocation.AllocationStrategy;
import io.genfin.pricing.id.CatalogId;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * One catalog line's share of a {@link Money} total split across several {@link CatalogId}s (e.g.
 * spreading an order-level discount or credit across the lines it applies to). A thin,
 * pricing-domain wrapper over {@code fin-money}'s {@link AllocationStrategy}, which performs the
 * actual lossless split - fin-pricing bakes in no allocation arithmetic of its own.
 */
public record PriceAllocation(CatalogId catalogId, Money amount) implements ValueObject {

  public PriceAllocation {
    Validate.notNull(catalogId, "catalogId must not be null.");
    Validate.notNull(amount, "amount must not be null.");
  }

  public static List<PriceAllocation> allocate(
      Money total,
      List<CatalogId> catalogIds,
      List<BigDecimal> weights,
      AllocationStrategy strategy) {
    Validate.notNull(catalogIds, "catalogIds must not be null.");
    Validate.notNull(strategy, "strategy must not be null.");
    List<Money> shares = strategy.allocate(total, weights);
    Validate.argument(
        shares.size() == catalogIds.size(), "allocated shares must match catalogIds size.");
    List<PriceAllocation> allocations = new ArrayList<>(catalogIds.size());
    for (int i = 0; i < catalogIds.size(); i++) {
      allocations.add(new PriceAllocation(catalogIds.get(i), shares.get(i)));
    }
    return List.copyOf(allocations);
  }
}
