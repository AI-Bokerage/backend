package io.genfin.reconciliation.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.SerializationException;
import io.genfin.reconciliation.report.ReportEntry;
import org.junit.jupiter.api.Test;

class ReportEntryCodecTest {

  @Test
  void reportEntryRoundTrips() {
    ReportEntryCodec codec = new ReportEntryCodec();
    ReportEntry original = ReportEntry.of("Matched Count", "42");

    ReportEntry roundTripped = codec.deserialize(codec.serialize(original));

    assertThat(roundTripped).isEqualTo(original);
  }

  @Test
  void malformedPayloadFailsToDeserialize() {
    ReportEntryCodec codec = new ReportEntryCodec();

    assertThatThrownBy(() -> codec.deserialize("not-a-valid-payload".getBytes()))
        .isInstanceOf(SerializationException.class);
  }
}
