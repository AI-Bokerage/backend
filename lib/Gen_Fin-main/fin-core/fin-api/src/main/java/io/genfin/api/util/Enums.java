package io.genfin.api.util;

import java.util.Optional;

public final class Enums {

  private Enums() {}

  public static <E extends Enum<E>> Optional<E> safeValueOf(Class<E> type, String name) {
    if (name == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(Enum.valueOf(type, name));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
