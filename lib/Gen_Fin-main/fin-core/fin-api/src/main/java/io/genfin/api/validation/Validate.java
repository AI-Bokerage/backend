package io.genfin.api.validation;

import io.genfin.api.exception.ValidationException;

/**
 * Argument and state validation helpers. Throws {@link ValidationException} / {@link
 * IllegalStateException}.
 */
public final class Validate {

  private Validate() {}

  public static <T> T notNull(T value, String message) {
    if (value == null) {
      throw new ValidationException(message);
    }
    return value;
  }

  public static String notBlank(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new ValidationException(message);
    }
    return value;
  }

  public static int positive(int value, String message) {
    if (value <= 0) {
      throw new ValidationException(message);
    }
    return value;
  }

  public static long positive(long value, String message) {
    if (value <= 0) {
      throw new ValidationException(message);
    }
    return value;
  }

  public static int nonNegative(int value, String message) {
    if (value < 0) {
      throw new ValidationException(message);
    }
    return value;
  }

  public static long nonNegative(long value, String message) {
    if (value < 0) {
      throw new ValidationException(message);
    }
    return value;
  }

  public static void required(boolean condition, String message) {
    if (!condition) {
      throw new ValidationException(message);
    }
  }

  public static void state(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException(message);
    }
  }

  public static void argument(boolean condition, String message) {
    if (!condition) {
      throw new IllegalArgumentException(message);
    }
  }
}
