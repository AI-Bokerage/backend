package io.genfin.payment.reference;

import io.genfin.api.domain.ValueObject;
import java.util.List;

public final class ReferenceCollection implements ValueObject {

  private static final ReferenceCollection EMPTY = new ReferenceCollection(List.of());

  private final List<Reference> references;

  private ReferenceCollection(List<Reference> references) {
    this.references = List.copyOf(references);
  }

  public static ReferenceCollection empty() {
    return EMPTY;
  }

  public static ReferenceCollection of(List<Reference> references) {
    return references.isEmpty() ? EMPTY : new ReferenceCollection(references);
  }

  public ReferenceCollection add(Reference reference) {
    List<Reference> updated = new java.util.ArrayList<>(references);
    updated.add(reference);
    return new ReferenceCollection(updated);
  }

  public List<Reference> all() {
    return references;
  }

  public List<Reference> byType(ReferenceType type) {
    return references.stream().filter(r -> r.type().code().equals(type.code())).toList();
  }

  public boolean isEmpty() {
    return references.isEmpty();
  }
}
