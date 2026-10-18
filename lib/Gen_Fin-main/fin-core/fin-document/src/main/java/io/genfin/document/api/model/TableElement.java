package io.genfin.document.api.model;

import io.genfin.api.validation.Validate;
import java.util.List;

/** A tabular {@link DocumentElement} with a header row and zero or more data rows. */
public final class TableElement implements DocumentElement {

  private final List<String> headers;
  private final List<List<String>> rows;

  private TableElement(List<String> headers, List<List<String>> rows) {
    this.headers = List.copyOf(Validate.notNull(headers, "TableElement headers must not be null"));
    this.rows =
        Validate.notNull(rows, "TableElement rows must not be null").stream()
            .map(List::copyOf)
            .toList();
  }

  public static TableElement of(List<String> headers, List<List<String>> rows) {
    return new TableElement(headers, rows);
  }

  public List<String> headers() {
    return headers;
  }

  public List<List<String>> rows() {
    return rows;
  }

  @Override
  public <R> R accept(DocumentElementVisitor<R> visitor) {
    return visitor.visitTable(this);
  }
}
