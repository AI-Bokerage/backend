package io.genfin.providerapi.descriptor;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

/**
 * The opaque, application-facing key a provider is registered under (e.g. "stripe", "razorpay").
 */
public final class ProviderId extends Identifier {

  private ProviderId(String value) {
    super(value);
  }

  public static ProviderId of(String value) {
    return new ProviderId(value);
  }

  public static ProviderId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static ProviderId generate(IdentifierGenerator generator) {
    return new ProviderId(generator.generate());
  }
}
