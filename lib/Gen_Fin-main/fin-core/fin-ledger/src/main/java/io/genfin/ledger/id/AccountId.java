package io.genfin.ledger.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class AccountId extends Identifier {

  private AccountId(String value) {
    super(value);
  }

  public static AccountId of(String value) {
    return new AccountId(value);
  }

  public static AccountId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static AccountId generate(IdentifierGenerator generator) {
    return new AccountId(generator.generate());
  }
}
