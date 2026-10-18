package io.genfin.invoice.builder;

import io.genfin.invoice.adjustment.Adjustment;
import io.genfin.invoice.adjustment.AdjustmentType;
import io.genfin.invoice.metadata.Metadata;
import io.genfin.money.money.Money;

/**
 * Builds {@link Adjustment}s. Consumers must not call the {@code Adjustment} constructor directly.
 */
public final class AdjustmentBuilder {

  private AdjustmentType type;
  private Money amount;
  private String reason;
  private Metadata metadata = Metadata.empty();

  private AdjustmentBuilder() {}

  public static AdjustmentBuilder newAdjustment() {
    return new AdjustmentBuilder();
  }

  public AdjustmentBuilder type(AdjustmentType type) {
    this.type = type;
    return this;
  }

  public AdjustmentBuilder amount(Money amount) {
    this.amount = amount;
    return this;
  }

  public AdjustmentBuilder reason(String reason) {
    this.reason = reason;
    return this;
  }

  public AdjustmentBuilder metadata(Metadata metadata) {
    this.metadata = metadata;
    return this;
  }

  public Adjustment build() {
    return new Adjustment(type, amount, reason, metadata);
  }
}
