package io.genfin.document.api.model;

public interface DocumentElement {
  <R> R accept(DocumentElementVisitor<R> visitor);
}
