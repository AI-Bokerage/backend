package io.genfin.api.id;

import io.genfin.api.internal.id.SupplierIdentifierGenerator;
import io.genfin.api.internal.id.TimeBasedUuidGenerator;
import io.genfin.api.internal.id.UuidV4Generator;
import io.genfin.api.port.id.IdentifierGenerator;
import io.genfin.api.port.time.ClockProvider;
import io.genfin.api.time.ClockProviders;
import java.util.function.Supplier;

/** Factory for {@link IdentifierGenerator} instances. Consumers must obtain generators here. */
public final class IdentifierGenerators {

  private static final IdentifierGenerator UUID_V4 = new UuidV4Generator();

  private IdentifierGenerators() {}

  public static IdentifierGenerator uuidV4() {
    return UUID_V4;
  }

  public static IdentifierGenerator timeBased(ClockProvider clockProvider) {
    return new TimeBasedUuidGenerator(clockProvider);
  }

  public static IdentifierGenerator timeBased() {
    return timeBased(ClockProviders.system());
  }

  public static IdentifierGenerator custom(Supplier<String> supplier) {
    return new SupplierIdentifierGenerator(supplier);
  }
}
