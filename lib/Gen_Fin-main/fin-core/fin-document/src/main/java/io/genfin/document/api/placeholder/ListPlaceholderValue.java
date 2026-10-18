package io.genfin.document.api.placeholder;

import java.util.List;

public final class ListPlaceholderValue implements PlaceholderValue {

  private final List<PlaceholderContext> items;

  private ListPlaceholderValue(List<PlaceholderContext> items) {
    this.items = List.copyOf(items);
  }

  public static ListPlaceholderValue of(List<PlaceholderContext> items) {
    return new ListPlaceholderValue(items);
  }

  public List<PlaceholderContext> items() {
    return items;
  }

  @Override
  public <R> R accept(PlaceholderValueVisitor<R> visitor) {
    return visitor.visitList(this);
  }
}
