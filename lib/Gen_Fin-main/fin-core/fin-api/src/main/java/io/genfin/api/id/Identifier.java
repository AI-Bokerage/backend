package io.genfin.api.id;

import io.genfin.api.exception.ValidationException;
import java.io.Serializable;
import java.util.Objects;

/**
 * Base class for strongly-typed identifiers. Two identifiers are only equal when they share both
 * the concrete type and the underlying value — a {@code RequestId} never equals a {@code
 * TransactionId} holding the same string.
 */
public abstract class Identifier implements Serializable {

  private final String value;

  protected Identifier(String value) {
    if (value == null || value.isBlank()) {
      throw new ValidationException("Identifier value must not be null or blank.");
    }
    this.value = value;
  }

  public final String value() {
    return value;
  }

  @Override
  public final boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof Identifier identifier)) {
      return false;
    }
    return getClass() == identifier.getClass() && value.equals(identifier.value);
  }

  @Override
  public final int hashCode() {
    return Objects.hash(getClass(), value);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" + value + "]";
  }
}
