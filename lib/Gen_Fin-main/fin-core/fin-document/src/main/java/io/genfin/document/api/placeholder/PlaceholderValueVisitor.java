package io.genfin.document.api.placeholder;

public interface PlaceholderValueVisitor<R> {
  R visitScalar(ScalarPlaceholderValue value);

  R visitList(ListPlaceholderValue value);

  R visitMissing(MissingPlaceholderValue value);
}
