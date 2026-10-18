package io.genfin.ledger.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class AccountingPeriodId extends Identifier {

  private AccountingPeriodId(String value) {
    super(value);
  }

  public static AccountingPeriodId of(String value) {
    return new AccountingPeriodId(value);
  }

  public static AccountingPeriodId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static AccountingPeriodId generate(IdentifierGenerator generator) {
    return new AccountingPeriodId(generator.generate());
  }
}
