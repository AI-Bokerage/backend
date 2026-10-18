package io.genfin.document.api.letterhead;

import io.genfin.api.exception.ValidationException;
import io.genfin.api.validation.Validate;
import io.genfin.document.api.identity.LetterheadId;
import java.util.Arrays;

public final class Letterhead {

  private final LetterheadId id;
  private final LetterheadFormat format;
  private final byte[] content;
  private final String mimeType;

  private Letterhead(LetterheadId id, LetterheadFormat format, byte[] content, String mimeType) {
    Validate.notNull(id, "id must not be null");
    Validate.notNull(format, "format must not be null");
    Validate.notNull(content, "content must not be null");
    Validate.notNull(mimeType, "mimeType must not be null");
    boolean isBlankFormat = StandardLetterheadFormat.BLANK.code().equals(format.code());
    if (!isBlankFormat && (content.length == 0 || mimeType.isBlank())) {
      throw new ValidationException("non-BLANK letterhead requires non-empty content and mimeType");
    }
    this.id = id;
    this.format = format;
    this.content = Arrays.copyOf(content, content.length);
    this.mimeType = mimeType;
  }

  public static Letterhead of(
      LetterheadId id, LetterheadFormat format, byte[] content, String mimeType) {
    return new Letterhead(id, format, content, mimeType);
  }

  public LetterheadId id() {
    return id;
  }

  public LetterheadFormat format() {
    return format;
  }

  public byte[] content() {
    return Arrays.copyOf(content, content.length);
  }

  public String mimeType() {
    return mimeType;
  }
}
