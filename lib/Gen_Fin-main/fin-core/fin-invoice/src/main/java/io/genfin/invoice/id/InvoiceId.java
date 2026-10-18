package io.genfin.invoice.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class InvoiceId extends Identifier {

  private InvoiceId(String value) {
    super(value);
  }

  public static InvoiceId of(String value) {
    return new InvoiceId(value);
  }

  public static InvoiceId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static InvoiceId generate(IdentifierGenerator generator) {
    return new InvoiceId(generator.generate());
  }
}
