package io.genfin.dunning.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class DunningCaseId extends Identifier {

  private DunningCaseId(String value) {
    super(value);
  }

  public static DunningCaseId of(String value) {
    return new DunningCaseId(value);
  }

  public static DunningCaseId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static DunningCaseId generate(IdentifierGenerator generator) {
    return new DunningCaseId(generator.generate());
  }
}
