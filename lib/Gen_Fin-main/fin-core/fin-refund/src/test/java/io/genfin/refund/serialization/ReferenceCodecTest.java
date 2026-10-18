package io.genfin.refund.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.SerializationException;
import io.genfin.refund.reference.Reference;
import org.junit.jupiter.api.Test;

class ReferenceCodecTest {

  @Test
  void referenceRoundTrips() {
    ReferenceCodec codec = new ReferenceCodec();
    Reference original = Reference.invoice("INV-1");

    Reference roundTripped = codec.deserialize(codec.serialize(original));

    assertThat(roundTripped).isEqualTo(original);
  }

  @Test
  void malformedPayloadFailsToDeserialize() {
    ReferenceCodec codec = new ReferenceCodec();

    assertThatThrownBy(() -> codec.deserialize("not-a-valid-payload".getBytes()))
        .isInstanceOf(SerializationException.class);
  }
}
