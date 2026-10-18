package io.genfin.document.internal.letterhead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.document.api.exception.LetterheadNotFoundException;
import io.genfin.document.api.identity.LetterheadId;
import io.genfin.document.api.letterhead.Letterhead;
import io.genfin.document.api.letterhead.StandardLetterheadFormat;
import io.genfin.document.port.LetterheadProvider;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DefaultLetterheadResolverTest {

  private static LetterheadProvider providerFor(String supportedId, String content) {
    return new LetterheadProvider() {
      @Override
      public boolean supports(LetterheadId id) {
        return LetterheadId.of(supportedId).equals(id);
      }

      @Override
      public Letterhead resolve(LetterheadId id) {
        return Letterhead.of(
            id,
            StandardLetterheadFormat.PDF,
            content.getBytes(StandardCharsets.UTF_8),
            "application/pdf");
      }
    };
  }

  @Test
  void resolvesThroughFirstSupportingProviderAmongMultiple() {
    DefaultLetterheadRegistry registry = new DefaultLetterheadRegistry();
    registry.register(providerFor("other-lh", "other-content"));
    registry.register(providerFor("lh-1", "lh-1-content"));
    DefaultLetterheadResolver resolver = new DefaultLetterheadResolver(registry);

    Letterhead resolved = resolver.resolve(LetterheadId.of("lh-1"));

    assertThat(new String(resolved.content(), StandardCharsets.UTF_8)).isEqualTo("lh-1-content");
  }

  @Test
  void firstRegisteredSupportingProviderWinsOverLaterOnes() {
    DefaultLetterheadRegistry registry = new DefaultLetterheadRegistry();
    registry.register(providerFor("lh-1", "first-content"));
    registry.register(providerFor("lh-1", "second-content"));
    DefaultLetterheadResolver resolver = new DefaultLetterheadResolver(registry);

    Letterhead resolved = resolver.resolve(LetterheadId.of("lh-1"));

    assertThat(new String(resolved.content(), StandardCharsets.UTF_8)).isEqualTo("first-content");
  }

  @Test
  void throwsForUnsupportedLetterheadId() {
    DefaultLetterheadResolver resolver =
        new DefaultLetterheadResolver(new DefaultLetterheadRegistry());

    assertThatThrownBy(() -> resolver.resolve(LetterheadId.of("missing")))
        .isInstanceOf(LetterheadNotFoundException.class);
  }
}
