package io.genfin.document.api.placeholder;

public interface PlaceholderValue {
  <R> R accept(PlaceholderValueVisitor<R> visitor);
}
