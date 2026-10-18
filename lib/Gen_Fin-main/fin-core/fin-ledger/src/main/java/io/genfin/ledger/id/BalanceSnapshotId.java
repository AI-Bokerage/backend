package io.genfin.ledger.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class BalanceSnapshotId extends Identifier {

  private BalanceSnapshotId(String value) {
    super(value);
  }

  public static BalanceSnapshotId of(String value) {
    return new BalanceSnapshotId(value);
  }

  public static BalanceSnapshotId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static BalanceSnapshotId generate(IdentifierGenerator generator) {
    return new BalanceSnapshotId(generator.generate());
  }
}
