package io.genfin.ledger.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class ChartOfAccountsId extends Identifier {

  private ChartOfAccountsId(String value) {
    super(value);
  }

  public static ChartOfAccountsId of(String value) {
    return new ChartOfAccountsId(value);
  }

  public static ChartOfAccountsId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static ChartOfAccountsId generate(IdentifierGenerator generator) {
    return new ChartOfAccountsId(generator.generate());
  }
}
