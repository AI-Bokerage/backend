package io.genfin.api.internal.id;

import io.genfin.api.port.id.IdentifierGenerator;
import java.util.function.Supplier;

public final class SupplierIdentifierGenerator implements IdentifierGenerator {

  private final Supplier<String> supplier;

  public SupplierIdentifierGenerator(Supplier<String> supplier) {
    this.supplier = supplier;
  }

  @Override
  public String generate() {
    return supplier.get();
  }
}
