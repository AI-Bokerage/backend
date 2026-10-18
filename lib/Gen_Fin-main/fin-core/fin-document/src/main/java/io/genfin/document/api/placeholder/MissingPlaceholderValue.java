package io.genfin.document.api.placeholder;

public final class MissingPlaceholderValue implements PlaceholderValue {

  private static final MissingPlaceholderValue INSTANCE = new MissingPlaceholderValue();

  private MissingPlaceholderValue() {}

  public static MissingPlaceholderValue instance() {
    return INSTANCE;
  }

  @Override
  public <R> R accept(PlaceholderValueVisitor<R> visitor) {
    return visitor.visitMissing(this);
  }
}
