package io.genfin.pricing.catalog;

import io.genfin.api.domain.Entity;
import io.genfin.api.validation.Validate;
import io.genfin.pricing.id.CatalogId;
import java.util.Optional;

/**
 * One priceable entry an application has registered into its {@link Catalog} - a plan, a fee, a
 * SKU, whatever the consuming application calls it. fin-pricing never hardcodes any actual product
 * or service; this type only carries the application's own {@link PricingReference} back to it,
 * plus the attributes/category the Pricing Pipeline's Catalog Resolution and Discount/Promotion
 * stages read.
 */
public final class CatalogItem extends Entity<CatalogId> {

  private final PricingReference reference;
  private final CatalogCategory category;
  private final CatalogAttributes attributes;
  private final CatalogMetadata metadata;

  public CatalogItem(CatalogId id, PricingReference reference) {
    this(id, reference, null, CatalogAttributes.empty(), CatalogMetadata.empty());
  }

  public CatalogItem(
      CatalogId id,
      PricingReference reference,
      CatalogCategory category,
      CatalogAttributes attributes,
      CatalogMetadata metadata) {
    super(id);
    this.reference = Validate.notNull(reference, "reference must not be null.");
    this.category = category;
    this.attributes = Validate.notNull(attributes, "attributes must not be null.");
    this.metadata = Validate.notNull(metadata, "metadata must not be null.");
  }

  public PricingReference reference() {
    return reference;
  }

  public Optional<CatalogCategory> category() {
    return Optional.ofNullable(category);
  }

  public CatalogAttributes attributes() {
    return attributes;
  }

  public CatalogMetadata metadata() {
    return metadata;
  }
}
