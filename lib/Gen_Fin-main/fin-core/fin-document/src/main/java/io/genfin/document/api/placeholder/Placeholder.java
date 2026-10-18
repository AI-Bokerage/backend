package io.genfin.document.api.placeholder;

import io.genfin.api.exception.ValidationException;

public final class Placeholder {

  private final String key;

  private Placeholder(String key) {
    if (key == null || key.isBlank()) {
      throw new ValidationException("Placeholder key must not be blank");
    }
    this.key = key;
  }

  public static Placeholder of(String key) {
    return new Placeholder(key);
  }

  public String key() {
    return key;
  }
}
