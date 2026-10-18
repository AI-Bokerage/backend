package io.genfin.document.api.letterhead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import io.genfin.document.api.identity.LetterheadId;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LetterheadTest {

  @Test
  void exposesGivenFields() {
    byte[] source = "fake-pdf-bytes".getBytes(StandardCharsets.UTF_8);
    Letterhead letterhead =
        Letterhead.of(
            LetterheadId.of("lh-1"), StandardLetterheadFormat.PDF, source, "application/pdf");

    assertThat(letterhead.id()).isEqualTo(LetterheadId.of("lh-1"));
    assertThat(letterhead.format()).isEqualTo(StandardLetterheadFormat.PDF);
    assertThat(letterhead.content()).isEqualTo(source);
    assertThat(letterhead.mimeType()).isEqualTo("application/pdf");
  }

  @Test
  void contentIsDefensivelyCopiedOnConstructionAndReturn() {
    byte[] source = "abc".getBytes(StandardCharsets.UTF_8);
    Letterhead letterhead =
        Letterhead.of(
            LetterheadId.of("lh-1"), StandardLetterheadFormat.PDF, source, "application/pdf");

    source[0] = (byte) 0xFF;
    assertThat(letterhead.content()).isEqualTo("abc".getBytes(StandardCharsets.UTF_8));

    byte[] returned = letterhead.content();
    returned[0] = (byte) 0xFF;
    assertThat(letterhead.content()).isEqualTo("abc".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void blankFormatSupportsEmptyContentArray() {
    Letterhead letterhead =
        Letterhead.of(LetterheadId.of("lh-blank"), StandardLetterheadFormat.BLANK, new byte[0], "");
    assertThat(letterhead.content()).isEmpty();
  }

  @Test
  void rejectsEmptyContentForNonBlankFormat() {
    assertThatThrownBy(
            () ->
                Letterhead.of(
                    LetterheadId.of("lh-1"),
                    StandardLetterheadFormat.PDF,
                    new byte[0],
                    "application/pdf"))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsBlankMimeTypeForNonBlankFormat() {
    assertThatThrownBy(
            () ->
                Letterhead.of(
                    LetterheadId.of("lh-1"),
                    StandardLetterheadFormat.PDF,
                    "bytes".getBytes(StandardCharsets.UTF_8),
                    ""))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsNullId() {
    assertThatThrownBy(
            () ->
                Letterhead.of(
                    null,
                    StandardLetterheadFormat.PDF,
                    "bytes".getBytes(StandardCharsets.UTF_8),
                    "application/pdf"))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsNullFormat() {
    assertThatThrownBy(
            () ->
                Letterhead.of(
                    LetterheadId.of("lh-1"),
                    null,
                    "bytes".getBytes(StandardCharsets.UTF_8),
                    "application/pdf"))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsNullContent() {
    assertThatThrownBy(
            () ->
                Letterhead.of(
                    LetterheadId.of("lh-1"), StandardLetterheadFormat.PDF, null, "application/pdf"))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void rejectsNullMimeType() {
    assertThatThrownBy(
            () ->
                Letterhead.of(
                    LetterheadId.of("lh-1"),
                    StandardLetterheadFormat.PDF,
                    "bytes".getBytes(StandardCharsets.UTF_8),
                    null))
        .isInstanceOf(ValidationException.class);
  }
}
