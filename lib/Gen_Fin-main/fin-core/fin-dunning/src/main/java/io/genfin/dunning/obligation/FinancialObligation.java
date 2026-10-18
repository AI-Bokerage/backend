package io.genfin.dunning.obligation;

import io.genfin.api.domain.Entity;
import io.genfin.api.validation.Validate;
import io.genfin.dunning.id.ObligationId;
import io.genfin.dunning.reference.Reference;
import io.genfin.money.money.Money;
import java.time.Instant;

/**
 * The central, domain-agnostic object fin-dunning collects against: an amount of money owed by a
 * given due date, of some application-defined {@link ObligationType}, backed by a {@link Reference}
 * to whatever the consuming application's real record is.
 *
 * <p>An invoice overdue balance, a subscription renewal charge, a loan installment, an EMI, a
 * marketplace settlement payable, or a vendor payment all fit through this one shape - fin-dunning
 * never assumes which. Everything downstream (policy resolution, reminder/retry/escalation
 * planning) operates on this type alone, never on an application's own domain classes.
 */
public final class FinancialObligation extends Entity<ObligationId> {

  private final Money amount;
  private final Instant dueDate;
  private final ObligationType type;
  private final Reference source;

  public FinancialObligation(
      ObligationId id, Money amount, Instant dueDate, ObligationType type, Reference source) {
    super(id);
    this.amount = Validate.notNull(amount, "amount must not be null.");
    Validate.argument(!amount.isNegative(), "amount must not be negative.");
    this.dueDate = Validate.notNull(dueDate, "dueDate must not be null.");
    this.type = Validate.notNull(type, "type must not be null.");
    this.source = Validate.notNull(source, "source must not be null.");
  }

  public static FinancialObligation of(
      Money amount, Instant dueDate, ObligationType type, Reference source) {
    return new FinancialObligation(ObligationId.generate(), amount, dueDate, type, source);
  }

  public Money amount() {
    return amount;
  }

  public Instant dueDate() {
    return dueDate;
  }

  public ObligationType type() {
    return type;
  }

  public Reference source() {
    return source;
  }

  /** True when {@code asOf} is strictly after the due date - i.e. the obligation is overdue. */
  public boolean isOverdueAsOf(Instant asOf) {
    Validate.notNull(asOf, "asOf must not be null.");
    return asOf.isAfter(dueDate);
  }
}
