package io.genfin.invoice.factory;

import io.genfin.invoice.metadata.CustomField;
import io.genfin.invoice.metadata.Metadata;
import io.genfin.invoice.metadata.TypedValue;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds {@link Metadata} from plain Java values, auto-detecting each value's {@link TypedValue}
 * kind.
 */
public final class MetadataFactory {

  private MetadataFactory() {}

  public static Metadata fromValues(Map<String, Object> rawValues) {
    List<CustomField> fields = new ArrayList<>();
    rawValues.forEach((key, value) -> fields.add(new CustomField(key, typedValueOf(value))));
    return Metadata.of(fields);
  }

  private static TypedValue typedValueOf(Object value) {
    return switch (value) {
      case String s -> TypedValue.ofString(s);
      case BigDecimal n -> TypedValue.ofNumber(n);
      case Boolean b -> TypedValue.ofBoolean(b);
      case Instant i -> TypedValue.ofInstant(i);
      default -> TypedValue.ofString(String.valueOf(value));
    };
  }
}
