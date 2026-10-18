package io.genfin.invoice.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.invoice.reference.Reference;
import io.genfin.invoice.reference.StandardReferenceType;
import org.junit.jupiter.api.Test;

class ReferenceCodecTest {

  @Test
  void referenceRoundTripsWithLabel() {
    ReferenceCodec codec = new ReferenceCodec();
    Reference original =
        Reference.of(StandardReferenceType.of("PROJECT"), "PRJ-1", "Website Redesign");

    Reference roundTripped = codec.deserialize(codec.serialize(original));

    assertThat(roundTripped).isEqualTo(original);
  }

  @Test
  void referenceRoundTripsWithoutLabel() {
    ReferenceCodec codec = new ReferenceCodec();
    Reference original = Reference.of(StandardReferenceType.of("SUBSCRIPTION"), "SUB-1");

    Reference roundTripped = codec.deserialize(codec.serialize(original));

    assertThat(roundTripped.displayLabel()).isNull();
    assertThat(roundTripped.type().code()).isEqualTo("SUBSCRIPTION");
    assertThat(roundTripped.value()).isEqualTo(original.value());
  }
}
