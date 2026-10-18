package io.genfin.payment.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.payment.reference.Reference;
import org.junit.jupiter.api.Test;

class ReferenceCodecTest {

  @Test
  void referenceRoundTrips() {
    ReferenceCodec codec = new ReferenceCodec();
    Reference original = Reference.invoice("INV-1");

    Reference roundTripped = codec.deserialize(codec.serialize(original));

    assertThat(roundTripped).isEqualTo(original);
  }
}
