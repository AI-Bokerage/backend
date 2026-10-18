package io.genfin.money.port.tax;

import io.genfin.api.port.spi.Extension;
import io.genfin.money.tax.TaxCategory;
import io.genfin.money.tax.TaxMetadata;
import java.util.Optional;

/**
 * Supplies opaque, jurisdiction-defined tax rules keyed by category and metadata. {@code R} is
 * deliberately generic — GST slabs, VAT tables, and Sales Tax nexus rules all differ in shape, and
 * none of that shape is known here.
 */
public interface TaxRulesProvider<R> extends Extension {

  Optional<R> rulesFor(TaxCategory category, TaxMetadata metadata);
}
