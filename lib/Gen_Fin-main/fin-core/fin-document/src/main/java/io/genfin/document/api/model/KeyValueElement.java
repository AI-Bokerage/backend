package io.genfin.document.api.model;

import io.genfin.api.exception.ValidationException;

public final class KeyValueElement implements DocumentElement {

  private final String label;
  private final String value;

  private KeyValueElement(String label, String value) {
    if (label == null || label.isBlank()) {
      throw new ValidationException("KeyValueElement label must not be blank");
    }
    this.label = label;
    this.value = value == null ? "" : value;
  }

  public static KeyValueElement of(String label, String value) {
    return new KeyValueElement(label, value);
  }

  public String label() {
    return label;
  }

  public String value() {
    return value;
  }

  @Override
  public <R> R accept(DocumentElementVisitor<R> visitor) {
    return visitor.visitKeyValue(this);
  }
}
