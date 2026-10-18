package io.genfin.dunning.obligation;

import io.genfin.dunning.id.ObligationId;
import io.genfin.dunning.reference.Reference;
import io.genfin.money.money.Money;
import java.time.Instant;

/**
 * Builds {@link FinancialObligation}s. Preferred over the canonical constructor for readability at
 * call sites given how many collaborators an obligation carries (amount, due date, type, source
 * reference). Mirrors {@code io.genfin.ledger.account.AccountBuilder}.
 */
public final class FinancialObligationBuilder {

  private ObligationId id;
  private Money amount;
  private Instant dueDate;
  private ObligationType type;
  private Reference source;

  private FinancialObligationBuilder() {}

  public static FinancialObligationBuilder newObligation() {
    return new FinancialObligationBuilder();
  }

  public FinancialObligationBuilder id(ObligationId id) {
    this.id = id;
    return this;
  }

  public FinancialObligationBuilder amount(Money amount) {
    this.amount = amount;
    return this;
  }

  public FinancialObligationBuilder dueDate(Instant dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public FinancialObligationBuilder type(ObligationType type) {
    this.type = type;
    return this;
  }

  public FinancialObligationBuilder source(Reference source) {
    this.source = source;
    return this;
  }

  public FinancialObligation build() {
    return new FinancialObligation(
        id == null ? ObligationId.generate() : id, amount, dueDate, type, source);
  }
}
