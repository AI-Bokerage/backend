package io.genfin.invoice.invoice;

import io.genfin.api.domain.ValueObject;
import java.time.Instant;
import java.util.Optional;

/** Invoice-relevant dates. {@code issueDate} is absent until the invoice is issued. */
public record InvoiceDates(Instant issueDate, Instant dueDate) implements ValueObject {

  public static InvoiceDates unissued(Instant dueDate) {
    return new InvoiceDates(null, dueDate);
  }

  public Optional<Instant> issueDateOptional() {
    return Optional.ofNullable(issueDate);
  }

  public InvoiceDates withIssueDate(Instant issueDate) {
    return new InvoiceDates(issueDate, dueDate);
  }
}
