package io.genfin.dunning.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class DunningPolicyId extends Identifier {

  private DunningPolicyId(String value) {
    super(value);
  }

  public static DunningPolicyId of(String value) {
    return new DunningPolicyId(value);
  }

  public static DunningPolicyId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static DunningPolicyId generate(IdentifierGenerator generator) {
    return new DunningPolicyId(generator.generate());
  }
}
