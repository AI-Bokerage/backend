package io.genfin.document.port;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.identity.LetterheadId;
import io.genfin.document.api.letterhead.Letterhead;
import io.genfin.document.api.letterhead.StandardLetterheadFormat;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LetterheadResolversTest {

  @Test
  void registerThenResolveRoundTrips() {
    LetterheadResolvers.Bundle bundle = LetterheadResolvers.standard();
    LetterheadProvider provider =
        new LetterheadProvider() {
          @Override
          public boolean supports(LetterheadId id) {
            return LetterheadId.of("lh-1").equals(id);
          }

          @Override
          public Letterhead resolve(LetterheadId id) {
            return Letterhead.of(
                id,
                StandardLetterheadFormat.PDF,
                "content".getBytes(StandardCharsets.UTF_8),
                "application/pdf");
          }
        };

    bundle.register(provider);

    Letterhead resolved = bundle.resolver().resolve(LetterheadId.of("lh-1"));
    assertThat(new String(resolved.content(), StandardCharsets.UTF_8)).isEqualTo("content");
  }
}
