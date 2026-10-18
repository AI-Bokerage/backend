package io.genfin.pricing.catalog;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.pricing.id.CatalogId;
import java.util.List;

/**
 * An application-defined grouping of {@link CatalogItem}s (e.g. a bundle, a plan family) used to
 * scope which items a Discount/Promotion/Coupon Engine rule applies across.
 */
public record CatalogGroup(String code, List<CatalogId> itemIds) implements ValueObject {

  public CatalogGroup {
    Validate.notBlank(code, "code must not be blank.");
    itemIds = List.copyOf(itemIds);
  }
}
