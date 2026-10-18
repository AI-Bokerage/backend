package io.genfin.api.util;

public final class Strings {

  private Strings() {}

  public static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  public static boolean isNotBlank(String value) {
    return !isBlank(value);
  }

  public static String defaultIfBlank(String value, String fallback) {
    return isBlank(value) ? fallback : value;
  }

  public static String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength);
  }
}
