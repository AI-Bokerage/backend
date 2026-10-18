package io.genfin.document.api.placeholder;

public final class ScalarPlaceholderValue implements PlaceholderValue {

  private final String value;

  private ScalarPlaceholderValue(String value) {
    this.value = value == null ? "" : value;
  }

  public static ScalarPlaceholderValue of(String value) {
    return new ScalarPlaceholderValue(value);
  }

  public String value() {
    return value;
  }

  @Override
  public <R> R accept(PlaceholderValueVisitor<R> visitor) {
    return visitor.visitScalar(this);
  }
}
