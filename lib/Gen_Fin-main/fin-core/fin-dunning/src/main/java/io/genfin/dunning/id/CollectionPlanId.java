package io.genfin.dunning.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class CollectionPlanId extends Identifier {

  private CollectionPlanId(String value) {
    super(value);
  }

  public static CollectionPlanId of(String value) {
    return new CollectionPlanId(value);
  }

  public static CollectionPlanId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static CollectionPlanId generate(IdentifierGenerator generator) {
    return new CollectionPlanId(generator.generate());
  }
}
