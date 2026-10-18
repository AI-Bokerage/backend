package io.genfin.invoice.metadata;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.exception.ValidationException;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single metadata value, tagged with its {@link ValueType} so readers can consume it without
 * casting blind.
 */
public record TypedValue(ValueType type, Object value) implements ValueObject {

  public static TypedValue ofString(String value) {
    return new TypedValue(ValueType.STRING, value);
  }

  public static TypedValue ofNumber(BigDecimal value) {
    return new TypedValue(ValueType.NUMBER, value);
  }

  public static TypedValue ofBoolean(boolean value) {
    return new TypedValue(ValueType.BOOLEAN, value);
  }

  public static TypedValue ofInstant(Instant value) {
    return new TypedValue(ValueType.INSTANT, value);
  }

  public String asString() {
    return as(ValueType.STRING, String.class);
  }

  public BigDecimal asNumber() {
    return as(ValueType.NUMBER, BigDecimal.class);
  }

  public boolean asBoolean() {
    return as(ValueType.BOOLEAN, Boolean.class);
  }

  public Instant asInstant() {
    return as(ValueType.INSTANT, Instant.class);
  }

  private <T> T as(ValueType expected, Class<T> javaType) {
    if (type != expected) {
      throw new ValidationException("TypedValue is " + type + ", not " + expected + ".");
    }
    return javaType.cast(value);
  }
}
